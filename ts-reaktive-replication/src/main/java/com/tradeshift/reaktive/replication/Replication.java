package com.tradeshift.reaktive.replication;

import static io.vavr.control.Option.none;
import static io.vavr.control.Option.some;

import java.lang.reflect.Constructor;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tradeshift.reaktive.actors.AbstractStatefulPersistentActor;
import com.tradeshift.reaktive.akka.SharedActorMaterializer;
import com.tradeshift.reaktive.replication.actors.ReplicatedActorSharding;
import com.tradeshift.reaktive.replication.io.WebSocketDataCenterClient;
import com.tradeshift.reaktive.replication.io.WebSocketDataCenterServer;
import com.tradeshift.reaktive.ssl.SSLFactory;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;

import akka.Done;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Extension;
import akka.http.javadsl.ConnectionContext;
import akka.persistence.query.PersistenceQuery;
import akka.persistence.query.javadsl.CurrentEventsByPersistenceIdQuery;
import akka.persistence.query.javadsl.EventsByTagQuery;
import akka.persistence.query.javadsl.ReadJournal;
import akka.stream.ActorMaterializer;
import io.vavr.collection.Map;
import io.vavr.collection.Seq;
import io.vavr.collection.Vector;
import io.vavr.control.Option;

public class Replication implements Extension {
    private static final Logger log = LoggerFactory.getLogger(Replication.class);
    private static final ConcurrentHashMap<Class<?>, EventClassifier<?>> classifiers = new ConcurrentHashMap<>();
    
    public static Replication get(ActorSystem system) {
        return ReplicationId.INSTANCE.get(system);
    }
    
    private final ActorSystem system;
    private final Config config;
    
    private Option<CompletionStage<Done>> started = none();
    
    public Replication(ActorSystem system) {
        this.system = system;
        this.config = system.settings().config().getConfig("ts-reaktive.replication");
    }
    
    public String getLocalDataCenterName() {
        return config.getString("local-datacenter.name");
    }
    
    @SuppressWarnings("unchecked")
    public <E> EventClassifier<E> getEventClassifier(Class<E> eventType) {
        return (EventClassifier<E>) classifiers.computeIfAbsent(eventType, t -> {
            ConfigValue value = config.getConfig("event-classifiers").root().get(eventType.getName());
            if (value == null) {
                throw new IllegalArgumentException("You must configure ts-reaktive.replication.event-classifiers.\"" +
                    eventType.getName() + "\" with an EventClassifier implementation.");
            }
            String className = (String) value.unwrapped();
            try {
                Class<?> type = getClass().getClassLoader().loadClass(className);
                Constructor<?> constr = type.getDeclaredConstructor();
                constr.setAccessible(true);
                return (EventClassifier<E>) constr.newInstance();
            } catch (Exception e) {
                throw new IllegalArgumentException(e);
            }
        });
    }
    
    public <E> String getEventTag(Class<E> eventType) {
        return AbstractStatefulPersistentActor.getEventTag(system.settings().config(), eventType);
    }

    /**
     * Starts the replication subsystem for the given event types and ShardRegion actors. 
     * This method must only be invoked once per actor system; further invocations have no effect.
     * @param eventTypesAndShardRegions Event types and their shard region actors (started by {@link ReplicatedActorSharding})
     */
    public synchronized CompletionStage<Done> start(Map<Class<?>,ActorRef> eventTypesAndShardRegions) {
        if (started.isDefined()) {
            return started.get();
        }
        log.info("Starting replication for event types: {}", eventTypesAndShardRegions.keySet().map(c -> c.getSimpleName()));
            
        // will throw exception if the classifier is undefined, so we get an early error
        eventTypesAndShardRegions.keySet().forEach(this::getEventClassifier);
            
        CompletionStage<Done> serverStarted = new WebSocketDataCenterServer(system, eventTypesAndShardRegions.mapKeys(this::getEventTag))
            .getBinding()
            .thenApply(b -> Done.getInstance());

        ActorMaterializer materializer = SharedActorMaterializer.get(system);
        
        VisibilityCassandraSession session = new VisibilityCassandraSession(system, "visibilitySession");
        VisibilityRepository visibilityRepo = new VisibilityRepository(session);
        
        // We consider ourselves started when the HTTP binding succeeds, and we've successfully connected to cassandra.
        // The below client flows just start some child actors, so there's nothing to wait on.
        started = some(session.getUnderlying().thenCompose(sess -> serverStarted));
        
        eventTypesAndShardRegions.forEach((eventType, shardRegion) -> {
            String eventTag = getEventTag(eventType);
            Config remoteDatacenters = config.getConfig("remote-datacenters");
            
            Seq<DataCenter> remotes = Vector.ofAll(remoteDatacenters.root().keySet()).map(name -> {
                Config dcConfig = remoteDatacenters.getConfig(name);
                String url = dcConfig.getString("url") + "/events/" + eventTag;
                ConnectionContext connOpts = SSLFactory.createSSLContext(dcConfig.withFallback(config.getConfig("client")))
                        .map(sslCtx -> (ConnectionContext) ConnectionContext.https(sslCtx))
                        .getOrElse(ConnectionContext.noEncryption());
                return new WebSocketDataCenterClient(system, connOpts, name, url);
            });
            
            DataCenterRepository dataCenterRepository = new DataCenterRepository() {
                @Override
                protected Iterable<DataCenter> listRemotes() {
                    return remotes;
                }
                
                @Override
                public String getLocalName() {
                    return getLocalDataCenterName();
                }
            };
            
            ReadJournal journal = PersistenceQuery.get(system).getReadJournalFor(ReadJournal.class, config.getString("read-journal-plugin-id"));
            
            DataCenterForwarder.startAll(system, materializer, dataCenterRepository, visibilityRepo, eventType,
                (EventsByTagQuery)journal, (CurrentEventsByPersistenceIdQuery) journal);
        });
        
        return started.get();
    }

}

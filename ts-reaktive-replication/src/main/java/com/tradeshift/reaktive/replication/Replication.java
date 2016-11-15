package com.tradeshift.reaktive.replication;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tradeshift.reaktive.actors.AbstractStatefulPersistentActor;
import com.tradeshift.reaktive.protobuf.EventEnvelopeSerializer;
import com.tradeshift.reaktive.replication.io.WebSocketDataCenterClient;
import com.tradeshift.reaktive.replication.io.WebSocketDataCenterServer;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Extension;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.ConnectionContext;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.persistence.query.PersistenceQuery;
import akka.persistence.query.javadsl.CurrentEventsByPersistenceIdQuery;
import akka.persistence.query.javadsl.EventsByTagQuery;
import akka.persistence.query.javadsl.ReadJournal;
import akka.stream.ActorMaterializer;
import javaslang.collection.Seq;
import javaslang.collection.Vector;

public class Replication implements Extension {
    private static final Logger log = LoggerFactory.getLogger(Replication.class);
    
    public static Replication get(ActorSystem system) {
        return ReplicationId.INSTANCE.get(system);
    }
    
    private final Map<String,CompletionStage<Void>> started = new HashMap<>();
    private final ActorSystem system;
    private final Config config;
    
    public Replication(ActorSystem system) {
        this.system = system;
        this.config = system.settings().config();
    }
    
    public String getLocalDataCenterName() {
        return config.getString("ts-reaktive.replication.local-datacenter.name");
    }
    
    private static final Map<Class<?>, EventClassifier<?>> classifiers = new ConcurrentHashMap<>();
    @SuppressWarnings("unchecked")
    public <E> EventClassifier<E> getEventClassifier(Class<E> eventType) {
        return (EventClassifier<E>) classifiers.computeIfAbsent(eventType, t -> {
            ConfigValue value = config.getConfig("ts-reaktive.replication.event-classifiers").root().get(eventType.getName());
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
        return AbstractStatefulPersistentActor.getEventTag(config, eventType);
    }

    public CompletionStage<Void> start(Class<?> eventType, ActorRef shardRegion) {
        Config config = this.config.getConfig("ts-reaktive.replication");
        final String eventTag = getEventTag(eventType);
        
        synchronized(started) {
            if (started.containsKey(eventTag)) return started.get(eventTag);
            
            getEventClassifier(eventType); // will throw exception if the classifier is undefined, so we get an early error
            
            ActorMaterializer materializer = ActorMaterializer.create(system);
            Config tagConfig = config.hasPath(eventTag) ? config.getConfig(eventTag).withFallback(config) : config;
            EventEnvelopeSerializer serializer = new EventEnvelopeSerializer(system);
            
            Seq<DataCenter> remotes = Vector.ofAll(tagConfig.getConfig("remote-datacenters").root().entrySet()).map(e -> {
                String name = e.getKey();
                Config remote = ((ConfigObject) e.getValue()).toConfig();
                // FIXME add config options for ConnectionContext
                return new WebSocketDataCenterClient(system, ConnectionContext.noEncryption(), name, remote.getString("url"), serializer);
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
            
            VisibilityCassandraSession session = new VisibilityCassandraSession(system, materializer, "visibilitySession");
            VisibilityRepository visibilityRepo = new VisibilityRepository(session);
            ReadJournal journal = PersistenceQuery.get(system).getReadJournalFor(ReadJournal.class, config.getString("read-journal-plugin-id"));
            
            DataCenterForwarder.startAll(system, materializer, dataCenterRepository, visibilityRepo, eventType,
                (EventsByTagQuery)journal, (CurrentEventsByPersistenceIdQuery) journal);
            
            WebSocketDataCenterServer server = new WebSocketDataCenterServer(config.getConfig("server"), shardRegion);
            final int port = tagConfig.getInt("local-datacenter.port");
            final String host = tagConfig.getString("local-datacenter.host");
            log.debug("Binding to {}:{}", host, port);
            
            CompletionStage<ServerBinding> bind = Http.get(system).bindAndHandle(server.route().flow(system, materializer),
                ConnectHttp.toHost(host, port), materializer);
            
            // The returned future completes when both the HTTP binding is ready, and the cassandra visibility session has initialized.
            started.put(eventTag, bind.thenCompose(binding -> session.getUnderlying().thenApply(s -> binding)).thenApply(b -> {
                log.info("Listening on {}", b.localAddress());
                return null;
            }));
            return started.get(eventTag);
        }
    }

}

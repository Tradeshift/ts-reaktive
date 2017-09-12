package com.tradeshift.reaktive.testkit;

import static com.tradeshift.reaktive.testkit.Streams.toSeq;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;
import akka.persistence.inmemory.query.javadsl.InMemoryReadJournal;
import akka.persistence.query.EventEnvelope;
import akka.persistence.query.PersistenceQuery;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.testkit.TestProbe;
import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import io.vavr.collection.Seq;

/**
 * Base class for simple actor-level unit tests that can share a basic, non-clustered actor system, with a configured in-memory journal.
 * 
 * The actor system is configured to stop root actors if they throw an exception, rather than the default restart behaviour. For unit tests,
 * stopping is preferred since that can be more easily tested using {@link TestProbe.watch}.
 * 
 * Subclasses can customize the configuration by passing a (statically final) {@link com.typesafe.config.Config} to the constructor.
 */
public abstract class SharedActorSystemSpec {
    private static Map<Config,Config> configs = HashMap.empty();
    private static Map<Config,ActorSystem> systems = HashMap.empty();
    private static Map<Config,Materializer> materializers = HashMap.empty();
    private static Map<Config,InMemoryReadJournal> journals = HashMap.empty();
    
    protected final Config config;
    protected final ActorSystem system;
    protected final Materializer materializer;
    protected final InMemoryReadJournal journal;

    public SharedActorSystemSpec() {
        this(ConfigFactory.empty());
    }
    
    public SharedActorSystemSpec(Config additionalConfig) {
        synchronized (SharedActorSystemSpec.class) {
            if (!configs.containsKey(additionalConfig)) {
                Config config = additionalConfig
                    .withFallback(ConfigFactory.parseResources("com/tradeshift/reaktive/testkit/SharedActorSystemSpec.conf"))
                    .withFallback(ConfigFactory.defaultReference())
                    .resolve();
                configs = configs.put(additionalConfig, config);
                ActorSystem system = ActorSystem.create(config.getString("akka.name"), config);
                systems = systems.put(additionalConfig, system);
                materializers = materializers.put(additionalConfig, ActorMaterializer.create(system));
                journals = journals.put(additionalConfig, PersistenceQuery.get(system).getReadJournalFor(InMemoryReadJournal.class, InMemoryReadJournal.Identifier()));
            }
        }
        this.config = configs.apply(additionalConfig);
        this.system = systems.apply(additionalConfig);
        this.materializer = materializers.apply(additionalConfig);
        this.journal = journals.apply(additionalConfig);
    }
    
    /**
     * Returns the current complete sequence of events known to the in-memory journal for the given [persistenceId]. 
     */
    public Seq<EventEnvelope> journalEventsFor(String persistenceId) {
        try {
            return 
                journal.currentEventsByPersistenceId(persistenceId, 0, Integer.MAX_VALUE).runWith(toSeq(), materializer)
                .toCompletableFuture().get(1, TimeUnit.SECONDS); 
        } catch (TimeoutException e) {
            // should not occur, since getting "current" events should always return pretty much instantly.
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}

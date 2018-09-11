package com.tradeshift.reaktive.replication;

import static akka.pattern.PatternsCS.ask;
import static com.tradeshift.reaktive.protobuf.UUIDs.toProtobuf;
import static com.tradeshift.reaktive.testkit.Await.within;
import static com.tradeshift.reaktive.testkit.Await.eventuallyDo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.cuppa.Cuppa.describe;
import static org.forgerock.cuppa.Cuppa.it;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.forgerock.cuppa.junit.CuppaRunner;
import org.junit.runner.RunWith;

import com.google.common.io.Files;
import com.tradeshift.reaktive.replication.TestData.TestCommand;
import com.tradeshift.reaktive.replication.TestData.TestEvent;
import com.tradeshift.reaktive.replication.actors.UnknownActorException;
import com.tradeshift.reaktive.testkit.AkkaPersistence;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.persistence.cassandra.testkit.CassandraLauncher;
import io.vavr.Tuple;
import io.vavr.collection.HashMap;
import io.vavr.collection.Map;

@RunWith(CuppaRunner.class)
public class ReplicationIntegrationSpec {
    static {
        CassandraLauncher.start(Files.createTempDir(), CassandraLauncher.DefaultTestConfigResource(), true, 0);
        System.out.println("Started cassandra on port " + CassandraLauncher.randomPort());        
    }

    private static class DC {
        public final String name;
        public final int clusteringPort = CassandraLauncher.freePort();
        public final Config config;
        public final ActorSystem system;
        public final ActorRef shardRegion;
        
        public DC(final String name, final int httpPort, Map<String,Integer> remotes) {
            this.name = name;
            config = ConfigFactory.parseMap(HashMap.<String,Object>
                of("clustering.port", clusteringPort)
                .put("clustering.seed-port", clusteringPort)
                .put("ts-reaktive.replication.local-datacenter.name", name)
                .put("ts-reaktive.replication.server.host", "localhost")
                .put("ts-reaktive.replication.server.port", httpPort)
                .put("ts-reaktive.replication.cassandra.keyspace", "replication_" + name)
                .put("cassandra-journal.port", CassandraLauncher.randomPort())
                .put("clustering.name", name)
                .put("cassandra-journal.keyspace", name) // each datacenter is in its own cassandra keyspace
                .merge(
                    HashMap.ofEntries(remotes.map(r -> Tuple.of("ts-reaktive.replication.remote-datacenters." + r._1 + ".url", "wss://localhost:" + r._2)))
                )
                .toJavaMap()
                ).withFallback(ConfigFactory.parseResources("com/tradeshift/reaktive/replication/ReplicationIntegrationSpec.conf")).withFallback(ConfigFactory.defaultReference()).resolve();
            
            system = ActorSystem.create(config.getString("clustering.name"), config);
            shardRegion = ReplicatedTestActor.sharding.shardRegion(system);
            
            new AkkaPersistence(system).awaitPersistenceInit();
            
            try {
                System.out.println("*** AWAITING (" + name + ")");
                Replication.get(system).start(HashMap.of(TestEvent.class, shardRegion)).toCompletableFuture().get(30, TimeUnit.SECONDS);
                System.out.println("*** DONE (" + name + ")");
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                throw new RuntimeException(e);
            }
            
        }
        
        public String getName() {
            return name;
        }

        public void write(UUID id, String msg) {
            try {
                ask(shardRegion, TestCommand.newBuilder().setAggregateId(toProtobuf(id)).setWrite(TestCommand.Write.newBuilder().setMsg(msg)).build(), 5000)
                .toCompletableFuture().get(5000, TimeUnit.MILLISECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                throw new RuntimeException(e);
            }
        }
        
        public String read(UUID id) {
            try {
                return (String) ask(shardRegion, TestCommand.newBuilder().setAggregateId(toProtobuf(id)).setRead(TestCommand.Read.newBuilder()).build(), 5000)
                .toCompletableFuture().get(5000, TimeUnit.MILLISECONDS);
            } catch (ExecutionException x) {
                if (x.getCause() instanceof UnknownActorException || x.getMessage().contains("Nothing written yet")) {
                    return null;
                } else {
                    throw new RuntimeException(x);
                }
            } catch (InterruptedException | TimeoutException e) {
                throw new RuntimeException(e);
            }            
        }
    }
{
    describe("ts-reaktive-replication", () -> {
        it("should replicate individual events to another datacenter once the EventRepository indicates it", () -> {
            final int port1 = CassandraLauncher.freePort(); 
            final int port2 = CassandraLauncher.freePort(); 
            final DC dc1 = new DC("dc1", port1, HashMap.of("dc2", port2));
            final DC dc2 = new DC("dc2", port2, HashMap.of("dc1", port1));
            
            UUID id1 = UUID.fromString("e6c44a74-0fad-40fa-8c30-50716ce87ea6");
            dc1.write(id1, "dc:" + dc1.getName());
            dc1.write(id1, "hello");
            dc1.write(id1, "world");
            assertThat(dc1.read(id1)).isEqualTo("world");
            assertThat(dc2.read(id1)).isNull();
            dc1.write(id1, "dc:" + dc2.getName());
            dc1.write(id1, "onwards");
            within(50, TimeUnit.SECONDS).eventuallyDo(() -> {
                assertThat(dc2.read(id1)).isEqualTo("onwards");
            });
            
            dc1.write(id1, "moar");
            within(50, TimeUnit.SECONDS).eventuallyDo(() -> {
                assertThat(dc2.read(id1)).isEqualTo("moar");
            });
            
            // Should replicate to all datacenters if event classifier returns "*"
            UUID id2 = UUID.fromString("95b2a8ba-3960-4bca-b803-c6aec238e99a");
            dc1.write(id2, "dc:" + dc1.getName());
            dc1.write(id2, "hello");
            dc1.write(id2, "dc:*");
            dc1.write(id2, "world");
            
            within(50, TimeUnit.SECONDS).eventuallyDo(() -> {
                assertThat(dc2.read(id2)).isEqualTo("world");
            });
        });
        
        it("should replicate lots of events to another datacenter once the EventRepository indicates it", () -> {
            final int port1 = CassandraLauncher.freePort(); 
            final int port2 = CassandraLauncher.freePort(); 
            final DC dc1 = new DC("dc3", port1, HashMap.of("dc4", port2));
            final DC dc2 = new DC("dc4", port2, HashMap.of("dc3", port1));
            
            final int N = 100;
            final int M = 100;
            
            List<UUID> ids = new ArrayList<UUID>();
            for (int i = 0; i < N; i++) {
                UUID id = UUID.randomUUID();
                ids.add(id);
                dc1.write(id, "dc:" + dc1.getName());
                for (int j = 0; j < M; j++) {
                    dc1.write(id, "message" + j);
                }
            }
            
            final int split = ids.size() / 2;
            for (int i = 0; i < split; i++) {
                UUID id = ids.get(i);
                dc1.write(id, "dc:" + dc2.getName());
                for (int j = M; j < M*2; j++) {
                    dc1.write(id, "message" + j);
                }
                dc1.write(id, "onwards");                
            }
            
            within(90, TimeUnit.SECONDS).eventuallyDo(() -> {
                assertThat(dc2.read(ids.get(0))).isEqualTo("onwards");
                assertThat(dc2.read(ids.get(split - 1))).isEqualTo("onwards");
            });
            
            dc1.write(ids.get(1), "moar");
            within(90, TimeUnit.SECONDS).eventuallyDo(() -> {
                assertThat(dc2.read(ids.get(1))).isEqualTo("moar");
            });
        });
    });
    
}}

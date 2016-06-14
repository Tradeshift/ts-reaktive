package com.tradeshift.reaktive.cassandra;

import static com.tradeshift.reaktive.assertj.CompletionStageAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.cuppa.Cuppa.describe;
import static org.forgerock.cuppa.Cuppa.it;
import static org.forgerock.cuppa.Cuppa.when;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import javaslang.collection.Vector;

import org.forgerock.cuppa.junit.CuppaRunner;
import org.junit.runner.RunWith;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Status.Failure;
import akka.persistence.cassandra.testkit.CassandraLauncher;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.testkit.TestProbe;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Session;
import com.google.common.io.Files;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

@RunWith(CuppaRunner.class)
public class ResultSetActorPublisherSpec {
    private static final Config config;
    private static final ActorSystem system;
    private static final ActorMaterializer materializer;
    private static final Session session;
    
    private static <T> Sink<T,CompletionStage<Vector<T>>> toVector() {
        return Sink.fold(Vector.empty(), (v,t) -> v.append(t));
    }
    
    static {
        CassandraLauncher.start(Files.createTempDir(), CassandraLauncher.DefaultTestConfigResource(), true, 0);
        System.out.println("Started cassandra on port " + CassandraLauncher.randomPort());
        
        config = ConfigFactory.parseString(
                "cassandra-journal.port = " + CassandraLauncher.randomPort()  
            ).withFallback(ConfigFactory.defaultReference());
        system = ActorSystem.create("cassandraSpec", config);
        materializer = ActorMaterializer.create(system);
        session = Cluster.builder().withPort(config.getInt("cassandra-journal.port")).addContactPoint("127.0.0.1").build().connect();
        session.execute("CREATE KEYSPACE cassandraSpec WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor': 1 }");
    }
    
{
    describe("ResultSetActorPublisher", () -> {
        when("querying an empty table", () -> {
            it("should return an empty source", () -> {
                session.execute("CREATE TABLE cassandraSpec.emptytable (key text PRIMARY KEY)");
                ResultSetFuture query = session.executeAsync("SELECT key FROM cassandraSpec.emptytable WHERE key = '1'");
                Source<String, ActorRef> source = ResultSetActorPublisher.source(query, row -> row.getString("key"));
                assertThat(source.runWith(toVector(), materializer)).succeedsWith(Vector.empty());
            });
        });
        
        when("querying a table with content", () -> {
            it("should return a source with an entry per row", () -> {
                session.execute("CREATE TABLE cassandraSpec.fulltable (key text, value int, PRIMARY KEY (key, value))");
                for (int i = 0; i < 1000; i++) {
                    session.execute("INSERT INTO cassandraSpec.fulltable (key,value) VALUES (?,?)", "main", i);
                }
                ResultSetFuture query = session.executeAsync("SELECT value FROM cassandraSpec.fulltable WHERE key = ?", "main");
                Source<Integer, ActorRef> source = ResultSetActorPublisher.source(query, row -> row.getInt("value"));
                Vector<Integer> result = source.runWith(toVector(), materializer).toCompletableFuture().get(10, TimeUnit.SECONDS);
                assertThat(result).hasSize(1000);
                assertThat(result.get(0)).isEqualTo(0);
                assertThat(result.get(999)).isEqualTo(999);
            });
            
            it("should fail the source if the row mapper queries nonexisting columns", () -> {
                session.execute("CREATE TABLE cassandraSpec.simpletable (key text PRIMARY KEY)");
                session.execute("INSERT INTO cassandraSpec.simpletable (key) VALUES (?)", "main");
                ResultSetFuture query = session.executeAsync("SELECT key FROM cassandraSpec.simpletable WHERE key = ?", "main");
                Source<String, ActorRef> source = ResultSetActorPublisher.source(query, row -> row.getString("foobar"));
                TestProbe probe = TestProbe.apply(system);
                source.runWith(Sink.actorRef(probe.ref(), "done"), materializer);
                Failure failure = probe.expectMsgClass(Failure.class);
                assertThat(failure.cause()).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("is not a column");
            });            
        });
    });
}}

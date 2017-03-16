package com.tradeshift.reaktive.cassandra;

import static scala.compat.java8.FutureConverters.toJava;
import static scala.compat.java8.FutureConverters.toScala;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import javaslang.control.Option;
import scala.compat.java8.JFunction1;
import scala.concurrent.Future;
import akka.Done;
import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.persistence.cassandra.CassandraPluginConfig;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.Statement;

/**
 * Provides asynchronous, non-blocking access to a cassandra session.
 */
public class CassandraSession {
    private final akka.persistence.cassandra.CassandraSession delegate;
    private final Materializer materializer;
    
    public CassandraSession(ActorSystem system, Materializer materializer, String metricsCategory, Function<Session,CompletionStage<Void>> init) {
        this.materializer = materializer;
        CassandraPluginConfig config = new CassandraPluginConfig(system, system.settings().config().getConfig("cassandra-journal"));
        JFunction1<Session, Future<?>> f = session -> toScala(init.apply(session));
        this.delegate = new akka.persistence.cassandra.CassandraSession(system, config, system.dispatcher(), system.log(), metricsCategory, f);
    }
    
    public CompletionStage<Session> getUnderlying() {
        return toJava(delegate.underlying());
    }
    
    public CompletionStage<PreparedStatement> prepare(String stmt) {
        return toJava(delegate.prepare(stmt));
    }
    
    public CompletionStage<Done> executeWrite(Statement stmt) {
        return toJava(delegate.executeWrite(stmt)).thenApply(unit -> Done.getInstance());
    }

    public CompletionStage<Source<Row, NotUsed>> select(Statement stmt) {
        return getUnderlying().thenApply(session ->
            ResultSetActorPublisher.source(session.executeAsync(stmt), row -> row).mapMaterializedValue(actorRef -> NotUsed.getInstance()));
    }
    
    public CompletionStage<Option<Row>> selectOne(Statement stmt) {
        return select(stmt).thenCompose(src -> src.runWith(Sink.headOption(), materializer)).thenApply(Option::ofOptional);
    }
    
    public void close() {
        delegate.close();
    }
}

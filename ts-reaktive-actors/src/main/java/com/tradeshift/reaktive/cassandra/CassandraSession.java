package com.tradeshift.reaktive.cassandra;

import static com.tradeshift.reaktive.Lambdas.toScala;
import static scala.compat.java8.FutureConverters.toJava;
import static scala.compat.java8.FutureConverters.toScala;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.Statement;

import akka.actor.ActorSystem;
import akka.persistence.cassandra.CassandraPluginConfig;

/**
 * Provides asynchronous, non-blocking access to a cassandra session.
 */
public class CassandraSession {
    private final akka.persistence.cassandra.CassandraSession delegate;
    
    public CassandraSession(ActorSystem system, String metricsCategory, Function<Session,CompletionStage<Void>> init) {
        CassandraPluginConfig config = new CassandraPluginConfig(system, system.settings().config().getConfig("cassandra-journal"));
        this.delegate = new akka.persistence.cassandra.CassandraSession(system, config, system.dispatcher(), system.log(), metricsCategory, 
            toScala(init.andThen(f -> toScala(f))));
    }
    
    public CompletionStage<Session> getUnderlying() {
        return toJava(delegate.underlying());
    }
    
    public CompletionStage<PreparedStatement> prepare(String stmt) {
        return toJava(delegate.prepare(stmt));
    }
    
    public CompletionStage<Void> executeWrite(Statement stmt) {
        return toJava(delegate.executeWrite(stmt)).thenApply(unit -> null);
    }

    public CompletionStage<ResultSet> select(Statement stmt) {
        return toJava(delegate.select(stmt));
    }
    
    public void close() {
        delegate.close();
    }
}

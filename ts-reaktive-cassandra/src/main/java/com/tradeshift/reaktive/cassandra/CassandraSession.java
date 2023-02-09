package com.tradeshift.reaktive.cassandra;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import akka.Done;
import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.stream.alpakka.cassandra.DefaultSessionProvider;
import akka.stream.javadsl.Source;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.cql.Statement;
import com.datastax.oss.driver.shaded.guava.common.util.concurrent.Runnables;
import io.vavr.collection.Seq;
import io.vavr.control.Option;
/**
 * Provides asynchronous, non-blocking access to a cassandra session.
 */
public class CassandraSession {
    private final akka.stream.alpakka.cassandra.javadsl.CassandraSession delegate;

    public CassandraSession(ActorSystem system, String metricsCategory, Function<CqlSession,CompletionStage<Done>> init) {
        this.delegate = new akka.stream.alpakka.cassandra.javadsl.CassandraSession(system,
            new DefaultSessionProvider(system, system.settings().config().getConfig("cassandra-journal")),
            system.dispatcher(), system.log(), metricsCategory, init, Runnables.doNothing());
    }

    public CassandraSession(ActorSystem system, String metricsCategory, Seq<String> initializationStatements) {
        this(system, metricsCategory, s -> executeInitStatements(s, initializationStatements));
    }

    public static CompletionStage<Done> executeInitStatements(CqlSession session, Seq<String> statements) {
        if (statements.isEmpty()) {
            return CompletableFuture.completedFuture(Done.getInstance());
        }

        return session.executeAsync(statements.head()).thenCompose(rs -> executeInitStatements(session, statements.tail()));
    }

    public CompletionStage<CqlSession> getUnderlying() {
        return delegate.underlying();
    }

    public CompletionStage<PreparedStatement> prepare(String stmt) {
        return delegate.prepare(stmt);
    }

    public CompletionStage<Done> executeWrite(Statement stmt) {
        return delegate.executeWrite(stmt);
    }

    public CompletionStage<Done> executeCreateTable(String stmt) {
        return delegate.executeCreateTable(stmt);
    }

    public Source<Row, NotUsed> select(Statement stmt) {
        return delegate.select(stmt);
    }

    public CompletionStage<Option<Row>> selectOne(Statement stmt) {
        return delegate.selectOne(stmt).thenApply(Option::ofOptional);
    }
}

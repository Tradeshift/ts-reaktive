package com.tradeshift.reaktive.cassandra;

import static com.tradeshift.reaktive.ListenableFutures.toJava;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.Statement;

import akka.Done;
import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.persistence.cassandra.ConfigSessionProvider;
import akka.persistence.cassandra.session.CassandraSessionSettings;
import akka.stream.javadsl.Source;
import io.vavr.collection.Seq;
import io.vavr.control.Option;
/**
 * Provides asynchronous, non-blocking access to a cassandra session.
 */
public class CassandraSession {
    private final akka.persistence.cassandra.session.javadsl.CassandraSession delegate;

    public CassandraSession(ActorSystem system, String metricsCategory, Function<Session,CompletionStage<Done>> init) {
        this.delegate = new akka.persistence.cassandra.session.javadsl.CassandraSession(system,
            new ConfigSessionProvider(system, system.settings().config().getConfig("cassandra-journal")),
            new CassandraSessionSettings(system.settings().config().getConfig("cassandra-journal")),
            system.dispatcher(), system.log(), metricsCategory, init);
    }

    public CassandraSession(ActorSystem system, String metricsCategory, Seq<String> initializationStatements) {
        this(system, metricsCategory, s -> executeInitStatements(s, initializationStatements));
    }

    private static CompletionStage<Done> executeInitStatements(Session session, Seq<String> statements) {
        if (statements.isEmpty()) {
            return CompletableFuture.completedFuture(Done.getInstance());
        }

        return toJava(session.executeAsync(statements.head())).thenCompose(rs -> executeInitStatements(session, statements.tail()));
    }

    public CompletionStage<Session> getUnderlying() {
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

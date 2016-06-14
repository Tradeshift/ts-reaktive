package com.tradeshift.reaktive.cassandra;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Row;
import com.google.common.util.concurrent.MoreExecutors;
import com.tradeshift.reaktive.akka.AbstractActorPublisherWithStash;

import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.actor.ActorPublisherMessage;
import akka.stream.javadsl.Source;
import scala.PartialFunction;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;
import scala.runtime.BoxedUnit;

public class ResultSetActorPublisher<T> extends AbstractActorPublisherWithStash<T> {
    public static <T> Source<T,ActorRef> source(ResultSetFuture rs, Function<Row,T> rowMapper) {
        return Source.<T>actorPublisher(Props.create(ResultSetActorPublisher.class, () -> new ResultSetActorPublisher<>(rs, rowMapper)));
    }
    
    private static class ExecTimeout {
        private static final ExecTimeout INSTANCE = new ExecTimeout();
    }
    
    private static class RowsFetched {
        private static final RowsFetched INSTANCE = new RowsFetched();
    }
    
    private static class ResultSetReady {
        public ResultSetReady(ResultSet rs) {
            this.rs = rs;
        }

        private final ResultSet rs;
    }
    
    private static final Logger log = LoggerFactory.getLogger(ResultSetActorPublisher.class);
    
    private final Cancellable timeoutJob;
    private final Function<Row, T> rowMapper;

    private ResultSetActorPublisher(ResultSetFuture rs, Function<Row,T> rowMapper) {
        this.rowMapper = rowMapper;
        FiniteDuration execTimeout = Duration.create(context().system().settings().config().getDuration("ts-reaktive.cassandra.select-query-timeout", TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS);
        timeoutJob = context().system().scheduler().scheduleOnce(execTimeout, self(), ExecTimeout.INSTANCE, context().dispatcher(), self());
        rs.addListener(() -> {
            try {
                self().tell(new ResultSetReady(rs.get()), self());
            } catch (InterruptedException | ExecutionException e) {
                // Won't occur, the listener is only invoked after .get() is available
                throw new RuntimeException(e);
            }
        }, MoreExecutors.directExecutor());
        
        receive(ReceiveBuilder.
            match(ResultSetReady.class, msg -> {
                timeoutJob.cancel();
                log.debug("ResultSet is ready with {} results, exhausted: {}", msg.rs.getAvailableWithoutFetching(), msg.rs.isExhausted());
                context().become(ready(msg.rs));
                deliver(msg.rs);
                unstashAll();
            })
            .match(ExecTimeout.class, msg -> {
                rs.cancel(true);
                onErrorThenStop(new TimeoutException("Timed out after " + execTimeout + " while waiting for query to start executing"));
            })
            .match(ActorPublisherMessage.Request.class, msg -> {
                // we ignore any requests for data while the query hasn't started yet. 
                // We'll explicitly call deliver() once data starts coming in anyways. 
            })
            .matchAny(other -> 
                stash()
            )
            .build());
    }
    
    private PartialFunction<Object, BoxedUnit> ready(ResultSet rs) {
        return ReceiveBuilder
            .match(ActorPublisherMessage.Request.class, msg -> 
                deliver(rs)
            )
            .match(ActorPublisherMessage.Cancel.class, msg ->
                context().stop(self())
            )
            .match(RowsFetched.class, msg ->
                deliver(rs)
            )
            .build();
    }

    private void deliver(ResultSet rs) {
        log.debug("delivering, {} available, exhausted: {}, demand: {}, completed: {}", rs.getAvailableWithoutFetching(), rs.isExhausted(), totalDemand(), isCompleted());
        if (rs.isExhausted()) {
            if (!isCompleted()) {
                onCompleteThenStop();
            }
        } else {
            if (totalDemand() > 0) {
                try {
                    readMore(rs);
                } catch (Exception x) {
                    onErrorThenStop(x);
                }
            }
        }
    }

    private void readMore(ResultSet rs) {
        final int available = rs.getAvailableWithoutFetching();
        if (available == 0) {
            log.info("Demanded more results, but no available. Fetching.");
            rs.fetchMoreResults().addListener(() -> 
                self().tell(RowsFetched.INSTANCE, self()), MoreExecutors.directExecutor()
            );
        } else {
            final int demand = (totalDemand() < Integer.MAX_VALUE) ? ((int)totalDemand()) : Integer.MAX_VALUE;
            log.debug("Have {} rows available, demand is {}", available, demand);
            for (int i = 0; i < Math.min(available, demand); i++) {
                onNext(rowMapper.apply(rs.one()));
            }
            deliver(rs);
        }
    }

    @Override
    public void postStop() {
        timeoutJob.cancel();
    }
}

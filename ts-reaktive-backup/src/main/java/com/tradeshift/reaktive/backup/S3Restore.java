package com.tradeshift.reaktive.backup;

import static com.tradeshift.reaktive.backup.DropUntilNext.dropUntilNext;
import static akka.pattern.PatternsCS.ask;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tradeshift.reaktive.akka.SharedActorMaterializer;
import com.tradeshift.reaktive.replication.actors.ReplicatedActorSharding;
import com.typesafe.config.Config;

import akka.actor.ActorRef;
import akka.actor.Status.Failure;
import akka.japi.pf.ReceiveBuilder;
import akka.persistence.AbstractPersistentActor;
import akka.persistence.RecoveryCompleted;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;
import akka.util.Timeout;
import scala.PartialFunction;
import scala.concurrent.duration.FiniteDuration;
import scala.runtime.BoxedUnit;

/**
 * Restores from an S3 bucket that S3Backup has written to.
 * 
 * It maintains progress as a persistent actor, deleting all but the most recent message, and only
 * creating an update once every minute. That should keep it in check, without needing to have
 * this depend on the file system or a specific storage implementation (e.g. cassandra)
 */
public class S3Restore extends AbstractPersistentActor {
    private static final Logger log = LoggerFactory.getLogger(S3Restore.class);
    
    private final Materializer materializer = SharedActorMaterializer.get(context().system());
    private final int maxInFlight;
    private final Timeout timeout;
    private final S3 s3;
    private final String tag;
    private final ActorRef shardRegion;
    private final FiniteDuration updateAccuracy;
    
    private long offset = 0;
    
    /**
     * Creates a new S3Restore actor. Restoration will start/resume immediately. When restore is complete, the
     * actor will stop.
     * 
     * @param s3 Repository to read from S3
     * @param tag Tag with which all events should be tagged
     * @param shardRegion {@link ReplicatedActorSharding} to talk to
     */
    public S3Restore(S3 s3, String tag, ActorRef shardRegion) {
        this.s3 = s3;
        this.tag = tag;
        this.shardRegion = shardRegion;
        
        Config config = context().system().settings().config().getConfig("ts-reaktive.backup.restore");
        maxInFlight = config.getInt("maxInFlight");
        timeout = Timeout.apply(config.getDuration("timeout", TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS);
        updateAccuracy = FiniteDuration.create(config.getDuration("update-accuracy", TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS);
    }
    
    @Override
    public PartialFunction<Object, BoxedUnit> receiveCommand() {
        return ReceiveBuilder
            .matchEquals("init", msg -> sender().tell("ack", self()))
            .match(Long.class, o -> {
                log.debug("Persisting {}", o);
                persist(o, done -> {
                    offset = o;
                    if (lastSequenceNr() > 1) {
                        deleteMessages(lastSequenceNr() - 1);
                    }
                    context().system().scheduler().scheduleOnce(updateAccuracy, sender(), "ack", context().dispatcher(), self());
                });
            })
            .match(Failure.class, msg -> {
                log.error("Stream failed, rethrowing", msg.cause());
                throw new RuntimeException(msg.cause());
            })
            .matchEquals("done", msg -> {
                log.debug("Completed, with offset now {}", offset);
                context().stop(self());
            })
            .build();
    }

    @Override
    public PartialFunction<Object, BoxedUnit> receiveRecover() {
        return ReceiveBuilder
            .match(Long.class, o -> offset = o)
            .match(RecoveryCompleted.class, msg -> startRestore())
            .build();
    }

    @Override
    public String persistenceId() {
        return "s3restore";
    }
    
    private void startRestore() {
        s3
        .list(tag)
        // skip over entries until the one BEFORE entry where startTime >= offset (since the one before may have been only partially restored)
        .via(dropUntilNext(l -> S3.getStartInstant(l).toEpochMilli() >= offset, true))
        .flatMapConcat(entry -> s3.loadEvents(entry.getKey().substring(entry.getKey().lastIndexOf("/") + 1)))
        .mapAsync(maxInFlight, e -> {
            log.debug("Replaying {}:{}", e.getPersistenceId(), e.getSequenceNr());
            return ask(shardRegion, e, timeout);
        })
        .map(resp -> {
            log.debug("Responded {}", resp);
            return (Long) resp;
        })
        // only save one lastOffset update per minute, and only the lowest one
        .conflate((Long l1, Long l2) -> l1 < l2 ? l1 : l2)
        .runWith(Sink.actorRefWithAck(self(), "init", "ack", "done", Failure::new), materializer);
    }
    
}

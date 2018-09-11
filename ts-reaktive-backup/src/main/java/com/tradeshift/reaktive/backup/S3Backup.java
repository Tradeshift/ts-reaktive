package com.tradeshift.reaktive.backup;

import static akka.pattern.PatternsCS.pipe;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tradeshift.reaktive.akka.SharedActorMaterializer;
import com.typesafe.config.Config;

import akka.Done;
import akka.actor.AbstractActor;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.Status.Failure;
import akka.cluster.singleton.ClusterSingletonManager;
import akka.cluster.singleton.ClusterSingletonManagerSettings;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Backoff;
import akka.pattern.BackoffSupervisor;
import akka.persistence.query.NoOffset;
import akka.persistence.query.javadsl.EventsByTagQuery;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;
import io.vavr.collection.Vector;
import scala.concurrent.duration.FiniteDuration;

/**
 * Makes a continuous backup of events onto an S3 bucket, grouping events into keys of predefined batch sizes.
 * 
 * Backup progress is stored on S3 as well.
 */
public class S3Backup extends AbstractActor {
    /**
     * Starts a ClusterSingletonManager + BackoffSupervisor that manages a continous backup to S3, restarting
     * and resuming automatically on crash or failure.
     * 
     * @param system The actor system to create the singleton for
     * @param query The akka persistence query journal to read events from
     * @param tag Tag to pass to the above query
     * @param s3 Service interface to communicate with S3
     */
    public static void start(ActorSystem system, EventsByTagQuery query, String tag, S3 s3) {
        system.actorOf(ClusterSingletonManager.props(
            BackoffSupervisor.props(
                Backoff.onFailure(
                    Props.create(S3Backup.class, () -> new S3Backup(query, tag, s3)),
                    "a",
                    FiniteDuration.create(1, TimeUnit.SECONDS),
                    FiniteDuration.create(1, TimeUnit.SECONDS), // TODO make these 3 configurable
                    0.2)
            ),
            Done.getInstance(),
            ClusterSingletonManagerSettings.create(system).withSingletonName("s")), "s3backup");
    }
    
    private static final Logger log = LoggerFactory.getLogger(S3Backup.class);
    
    private final Materializer materializer = SharedActorMaterializer.get(context().system());
    private final EventsByTagQuery query;
    private final String tag;
    private final S3 s3;
    private final int eventChunkSize;
    private final Duration eventChunkDuration;
    
    public S3Backup(EventsByTagQuery query, String tag, S3 s3) {
        this.query = query;
        this.tag = tag;
        this.s3 = s3;
        
        Config backupCfg = context().system().settings().config().getConfig("ts-reaktive.backup.backup");
        eventChunkSize = backupCfg.getInt("event-chunk-max-size");
        eventChunkDuration = backupCfg.getDuration("event-chunk-max-duration");
        
        pipe(s3.loadOffset(), context().dispatcher()).to(self());
    }

    @Override
    public Receive createReceive() {
    	return ReceiveBuilder.create()
            .match(Long.class, offset -> {
                getContext().become(startBackup(offset));
            })
            .build();
    }
    
    private Receive startBackup(long offset) {
        query
            .eventsByTag(tag, NoOffset.getInstance())
            // create backups of max [N] elements, or at least every [T] on activity
            // FIXME write a stage that, instead of buffering each chunk into memory, creates sub-streams instead.
            .groupedWithin(eventChunkSize, eventChunkDuration)
            .filter(list -> list.size() > 0)
            .mapAsync(4, list -> s3.store(tag, Vector.ofAll(list)).thenApply(done -> list.get(list.size() - 1).offset()))
            .runWith(Sink.actorRefWithAck(self(), "init", "ack", "done", Failure::new), materializer);
        
        return ReceiveBuilder.create()
            .matchEquals("init", msg -> sender().tell("ack", self()))
            .match(Long.class, l -> pipe(s3.saveOffset(l).thenApply(done -> "ack"), context().dispatcher()).to(sender()))
            .match(Failure.class, msg -> {
                log.error("Stream failed, rethrowing", msg.cause());
                throw new RuntimeException(msg.cause());
            })
            .matchEquals("done", msg -> { throw new IllegalStateException("eventsByTag completed, this should not happen. Killing actor, hoping for restart"); })
            .build();
    }
}

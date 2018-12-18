package com.tradeshift.reaktive.materialize;

import static io.vavr.control.Option.none;
import static io.vavr.control.Option.some;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

import com.tradeshift.reaktive.CompletableFutures;
import com.tradeshift.reaktive.akka.SharedActorMaterializer;
import com.typesafe.config.Config;

import akka.Done;
import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ReceiveTimeout;
import akka.actor.Status.Failure;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import akka.persistence.AbstractPersistentActor;
import akka.persistence.DeleteMessagesFailure;
import akka.persistence.DeleteMessagesSuccess;
import akka.persistence.RecoveryCompleted;
import akka.stream.ActorMaterializer;
import akka.stream.KillSwitch;
import akka.stream.KillSwitches;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import io.vavr.collection.Seq;
import io.vavr.collection.Set;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import scala.concurrent.duration.FiniteDuration;

/**
 * Persistent actor that reads events from an existing journal and creates a materialized view.
 *
 * @param <E> Type of events that the actor is going to read from the journal
 */
public abstract class MaterializerActor<E> extends AbstractPersistentActor {
    private static final String configPath = "ts-reaktive.actors.materializer";
    private static final CompletionStage<Done> done = completedFuture(Done.getInstance());

    protected final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
    private final MaterializerMetrics metrics = new MaterializerMetrics(
        // Turn CamelCase to camel-case.
        getClass().getSimpleName().replaceAll("([a-z])([A-Z]+)", "$1-$2").toLowerCase());
    private final FiniteDuration rollback;
    private final FiniteDuration updateAccuracy;
    private final FiniteDuration restartDelay;
    private final int batchSize;
    private final int maxEventsPerTimestamp;
    private final Duration updateOffsetInterval;
    private final AtomicReference<Instant> reimportProgress = new AtomicReference<>();
    private final ActorMaterializer materializer;

    private long offset = 0;
    private ActorRef stream = null;
    private boolean awaitingDeleteSuccess = false;
    private boolean awaitingRestart = false;
    private Option<KillSwitch> ongoingReimport = Option.none();

    protected MaterializerActor() {
        this.materializer = SharedActorMaterializer.get(context().system());
        Config config = context().system().settings().config().getConfig(configPath);
        rollback = FiniteDuration.create(config.getDuration("rollback", SECONDS), SECONDS);
        updateAccuracy = FiniteDuration.create(config.getDuration("update-accuracy", SECONDS), SECONDS);
        restartDelay = FiniteDuration.create(config.getDuration("restart-delay", SECONDS), SECONDS);
        batchSize = config.getInt("batch-size");
        maxEventsPerTimestamp = config.getInt("max-events-per-timestamp");
        updateOffsetInterval = config.getDuration("update-offset-interval");
        log.info("{} has started.", self().path());

        getContext().setReceiveTimeout(updateOffsetInterval);
    }

    @Override
    public void postRestart(Throwable reason) throws Exception {
        super.postRestart(reason);
        metrics.getRestarts().increment();
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
            .match(ReceiveTimeout.class, msg -> {
                recordOffsetMetric();
            })
            .match(CancelReimport.class, msg -> {
                cancelReimport();
            })
            .match(Reimport.class, msg -> {
                reimport(msg.entityIds);
            })
            .match(QueryReimport.class, msg -> {
                Instant progress = reimportProgress.get();
                sender().tell(progress != null ? progress : Done.getInstance(), self());
            })
            .matchEquals("reimportComplete", msg -> {
                log.info("Re-import completed.");
                this.ongoingReimport = none();
                reimportProgress.set(null);
            })
            .matchEquals("restartStream", msg -> {
                materializeEvents();
            })
            .matchEquals("init", msg -> getSender().tell("ack", self()))
            .match(Long.class, o -> !awaitingRestart, o -> {
                // We're now positively done with timestamp [o].
                // If [o] is long enough ago, we can start with [o+1] next time. Otherwise, we start again at now() minus rollback.
                log.debug("Done with timestamp {}", o);
                long now = System.currentTimeMillis();
                long newOffset;
                if (o < now - rollback.toMillis()) {
                    newOffset = o + 1;
                } else {
                    newOffset = now - rollback.toMillis();
                }
                stream = sender();
                if (log.isInfoEnabled()) {
                    log.info("Persisting offset {} / {}, which is {}s ago.", newOffset, Instant.ofEpochMilli(newOffset), (now - newOffset) / 1000);
                }
                persist(newOffset, done -> {
                    offset = newOffset;
                    recordOffsetMetric();
                    awaitingDeleteSuccess = true;
                    if (lastSequenceNr() > 1) {
                        deleteMessages(lastSequenceNr() - 1);
                    } else {
                        self().tell(new DeleteMessagesSuccess(0), self());
                    }
                });
            })
            .match(DeleteMessagesSuccess.class, msg -> {
                awaitingDeleteSuccess = false;
                unstashAll();
                context().system().scheduler()
                    .scheduleOnce(updateAccuracy, stream, "ack", context().dispatcher(), self());
            })
            .match(DeleteMessagesFailure.class, msg -> {
                awaitingDeleteSuccess = false;
                unstashAll();
                log.error(msg.cause(), "Delete messages failed at offset " + offset + ", rethrowing", msg.cause());
                throw (Exception) msg.cause();
            })
            .match(Failure.class, msg -> {
                log.error(msg.cause(), "Stream failed at offset " + offset + ", restarting in " + restartDelay);
                scheduleStreamRestart();
            })
            .matchEquals("done", msg -> {
                if (awaitingDeleteSuccess) {
                    stash();
                } else {
                    log.debug("Completed, now offset is: {}", offset);
                    scheduleStreamRestart();
                }
            })
            .matchEquals("reset", msg -> {
                log.info("Resetting importer and starting from scratch.");
                offset = 0L;
                persist(offset, done -> {
                    log.debug("Reset event stored, stopping actor and awaiting restart.");
                    awaitingRestart = true;
                    sender().tell(Done.getInstance(), self());
                    context().stop(self());
                });
            })
            .build();
    }

    private void scheduleStreamRestart() {
        context().system().scheduler()
            .scheduleOnce(restartDelay, self(), "restartStream", context().dispatcher(), self());
    }

    @Override
    public Receive createReceiveRecover() {
        return ReceiveBuilder.create()
            .match(Long.class, o -> offset = o)
            .match(RecoveryCompleted.class, m -> {
                materializeEvents();
            })
            .build();
    }

    @Override
    public String persistenceId() {
        return self().path().toString();
    }

    @Override
    public void onRecoveryFailure(Throwable cause, scala.Option<Object> event) {
        super.onRecoveryFailure(cause, event);

        if (event.isEmpty() && cause.getMessage().contains("saw unexpected seqNr")) {
            // this import actor has a corrupt journal, which can occur after a split brain. The safest (and only)
            // thing to do is a completely new re-import.
            log.error("Corrupt journal detected for {}. Please delete all messages and metadata for this persistence ID.",
                      persistenceId());
        }
    }

    @Override
    public void postStop() {
        cancelReimport();
    }

    private void materializeEvents() {
        log.debug("{}: Start materialize of events from offset: {}, with rollback: {}",
                  persistenceId(), offset, rollback);
        recordOffsetMetric();

        loadEvents(Instant.ofEpochMilli(offset))
            // get a Seq<E> of where each Seq has the same timestamp, or emit buffer after [rollback]
            // (assuming no events with that timestamp after that)
            .via(GroupWhile.apply((a,b) -> timestampOf(a).equals(timestampOf(b)),
                                  maxEventsPerTimestamp, rollback))

            // Allow multiple timestamps to be processed simultaneously
            .groupedWeightedWithin(maxEventsPerTimestamp, seq -> (long) seq.size(), Duration.ofSeconds(1))

            // Process them, and emit a single timestamp at t
            .mapAsync(1, listOfSeq ->
                // re-group into batchSize, each one no longer necessarily within one timestamp
                Source.from(Vector.ofAll(listOfSeq).flatMap(seq -> seq))
                    .grouped(batchSize)
                    .mapAsync(1, envelopeList -> materialize(envelopeList))
                    .runWith(Sink.ignore(), materializer)
                    .thenApply(done -> timestampOf(listOfSeq.get(listOfSeq.size() - 1).last()).toEpochMilli())
            )
            //keep highest last offset, in order to limit the amount of events sent to the journal
            .conflate(Long::max)
            .runWith(Sink.actorRefWithAck(self(), "init", "ack", "done", Failure::new), materializer);
    }

    private void reimport(Set<String> entityIds) {
        if (ongoingReimport.isDefined()) {
            sender().tell(new Failure(new IllegalStateException("A re-import is already in progress.")), self());
            return;
        }

        log.info("Starting a reimport for {} entities.", entityIds.size());
        ActorRef self = self();
        Instant maxTimestamp = Instant.now().minusMillis(rollback.toMillis());

        reimportProgress.set(Instant.EPOCH);
        ongoingReimport = some(Source.fromCompletionStage(preStartReimport(entityIds))
            .flatMapConcat(done -> loadEvents(Instant.EPOCH))
            .viaMat(KillSwitches.single(), Keep.right())
            .filter(e -> entityIds.contains(getEntityId(e).toString()))
            .takeWhile(e -> timestampOf(e).isBefore(maxTimestamp))
            .mapAsync(1, e -> materialize(e).thenApply(done -> {
                Instant t = timestampOf(e);
                reimportProgress.set(t);
                return t;
            }))
            .conflate((t1, t2) -> (t1.isAfter(t2)) ? t1 : t2)
            .throttle(1, Duration.ofSeconds(1))
            .map(t -> {
                metrics.getReimportRemaining().record(ChronoUnit.MILLIS.between(t, maxTimestamp));
                return Done.getInstance();
            })
            .toMat(Sink.onComplete(result -> self.tell("reimportComplete", self())), Keep.left())
            .run(materializer));

        sender().tell(Done.getInstance(), self());
    }

    /**
     * This method is invoked whenever a reimport is started with the given entity IDs.
     * Sub-classes can override this to do additional cleanup, beyond just re-importing the events.
     */
    protected CompletionStage<Done> preStartReimport(Set<String> entityIds) {
        return completedFuture(Done.getInstance());
    }

    private void cancelReimport() {
        ongoingReimport.forEach(k -> {
            log.info("Cancelling a reimport.");
            k.shutdown();
        });
        ongoingReimport = none();
        reimportProgress.set(null);

        sender().tell(Done.getInstance(), self());
    }

    private void recordOffsetMetric() {
        metrics.getOffset().record(System.currentTimeMillis() - offset);
    }

    protected abstract CompletionStage<Done> materialize(E envelope);

    /**
     * Returns the entity ID for the given envelope.
     */
    protected abstract String getEntityId(E envelope);

    /**
     * Returns a key that determines if two envelopes can be imported concurrently w.r.t. each other.
     * This defaults to {@link #getEntityId(Object)}.
     *
     * If you want your materializer to materialize everything sequentially, just always return the same string here.
     */
    protected String getConcurrencyKey(E envelope) {
        return getEntityId(envelope);
    }

    /**
     * Materialize the given envelopes in parallel, as far as their entityIds allow it.
     */
    private CompletionStage<Done> materialize(java.util.List<E> envelopes) {
        java.util.Map<Object, Seq<E>> map = new HashMap<>();
        for (E envelope: envelopes) {
            Object key = getConcurrencyKey(envelope);
            map.put(key, map.getOrDefault(key, Vector.empty()).append(envelope));
        }

        Seq<CompletionStage<Done>> futures = Vector.empty();
        for (Entry<Object, Seq<E>> e: map.entrySet()) {
            futures = futures.append(persistSequential(e.getValue()));
        }

        return CompletableFutures.sequence(futures.map(c -> c.toCompletableFuture())).thenApply(seqOfDone -> Done.getInstance());
    }

    private CompletionStage<Done> persistSequential(Seq<E> seq) {
        if (seq.isEmpty()) {
            return done;
        } else {
            return materialize(seq.head())
                .thenCompose(done -> {
                    metrics.getEvents().increment();
                    return persistSequential(seq.tail());
                });
        }
    }

    /**
     * Get a source of event envelopes with the timestamp higher than or equal to {@code since} parameter.
     * Event envelopes should be ordered by timestamp and then by sequence number.
     */
    protected abstract Source<E, NotUsed> loadEvents(Instant since);

    /**
     * Get a timestamp of event envelope.
     */
    public abstract Instant timestampOf(E envelope);

    /**
     * Message that can be sent to this actor to start a secondary re-import of certain UUIDs.
     * The main import stream will keep running while the re-import is underway.
     *
     * The re-import will scan the source journal for the specified ids from the start of the journal
     * until the time that the Reimport message was initially received. The ids are compared by string
     * representation of the actual getEntityId method, which should work fine for both strings and UUID.
     *
     * A re-import is transient and will not resume or continue when the actor system is restarted.
     *
     * Multiple concurrent re-imports are not supported; an in-progress Reimport needs to be cancelled
     * first through CancelReimport.
     */
    public static class Reimport implements Serializable {
        private static final long serialVersionUID = 1L;

        private final Set<String> entityIds;

        public Reimport(Set<String> entityIds) {
            this.entityIds = entityIds;
        }
    }

    /** Message that can be sent to this actor to cancel any on-going reimport. */
    public static class CancelReimport implements Serializable {
        private static final long serialVersionUID = 1L;

        public static final CancelReimport instance = new CancelReimport();
        private CancelReimport() {}
    }

    /**
     * Message that can be sent to this actor to query the progress of any on-going reimport.
     * It returns an Instant with the last-reimported timestamp, or Done if no import is in progress.
     */
    public static class QueryReimport implements Serializable {
        private static final long serialVersionUID = 1L;

        public static final QueryReimport instance = new QueryReimport();
        private QueryReimport() {}
    }
}

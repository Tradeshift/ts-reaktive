package com.tradeshift.reaktive.materialize;

import static io.vavr.control.Option.none;
import static io.vavr.control.Option.some;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import com.tradeshift.reaktive.CompletableFutures;
import com.tradeshift.reaktive.akka.SharedActorMaterializer;
import com.tradeshift.reaktive.protobuf.MaterializerActor.MaterializerActorEvent;
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
import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import io.vavr.collection.Seq;
import io.vavr.collection.Set;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import scala.concurrent.duration.FiniteDuration;

/**
 * Persistent actor that reads events from an existing journal and creates a materialized view.
 *
 * Multiple concurrent workers can be started, that import from different time periods simultaneously.
 *
 * It is up to the implementing class to synchronize any concurrent workers.
 *
 * @param <E> Type of events that the actor is going to read from the journal
 */
public abstract class MaterializerActor<E> extends AbstractPersistentActor {
    private static final String configPath = "ts-reaktive.actors.materializer";
    private static final CompletionStage<Done> done = completedFuture(Done.getInstance());

    protected final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
    private final MaterializerMetrics metrics = new MaterializerMetrics(
        // Turn CamelCase to camel-case.
        getClass().getSimpleName().replaceAll("([a-z])([A-Z]+)", "$1-$2").toLowerCase(), getAdditionalMetricTags());
    private final FiniteDuration rollback;
    private final FiniteDuration updateAccuracy;
    private final FiniteDuration restartDelay;
    private final int batchSize;
    private final int updateSize;
    private final int maxEventsPerTimestamp;
    private final int maxWorkerCount;
    private final int deleteMessagesAfter;
    private final Duration updateOffsetInterval;
    private final AtomicReference<Instant> reimportProgress = new AtomicReference<>();
    private final ActorMaterializer materializer;

    private volatile MaterializerWorkers workers;
    private Option<KillSwitch> ongoingReimport = Option.none();
    private Map<UUID,AtomicLong> workerEndTimestamps = HashMap.empty();

    protected MaterializerActor() {
        this.materializer = SharedActorMaterializer.get(context().system());
        Config config = context().system().settings().config().getConfig(configPath);
        rollback = FiniteDuration.create(config.getDuration("rollback", SECONDS), SECONDS);
        updateAccuracy = FiniteDuration.create(config.getDuration("update-accuracy", SECONDS), SECONDS);
        restartDelay = FiniteDuration.create(config.getDuration("restart-delay", SECONDS), SECONDS);
        batchSize = config.getInt("batch-size");
        updateSize = config.getInt("update-size");
        maxEventsPerTimestamp = config.getInt("max-events-per-timestamp");
        maxWorkerCount = config.getInt("max-worker-count");
        updateOffsetInterval = config.getDuration("update-offset-interval");
        deleteMessagesAfter = config.getInt("delete-messages-after");
        this.workers = MaterializerWorkers.empty(Duration.ofMillis(rollback.toMillis()));
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
            .match(QueryProgress.class, msg -> {
                sendProgress();
            })
            .matchEquals("reimportComplete", msg -> {
                log.info("Re-import completed.");
                this.ongoingReimport = none();
                reimportProgress.set(null);
            })
            .match(StartWorker.class, msg -> {
                materializeEvents(msg.worker);
            })
            .match(CreateWorker.class, msg -> {
                createWorker(msg.timestamp, msg.endTimestamp);
            })
            .matchEquals("init", msg -> getSender().tell("ack", self()))
            .match(WorkerProgress.class, p -> {
                persist(workers.onWorkerProgress(p.worker, p.timestamp), evt -> {
                    applyEvent(evt);
                    context().system().scheduler().scheduleOnce(
                        updateAccuracy, sender(), "ack", context().dispatcher(), self());
                    if ((lastSequenceNr() > 1) && ((lastSequenceNr() % deleteMessagesAfter) == 0)) {
                        log.debug("Deleting up to {}", lastSequenceNr() - 1);
                        deleteMessages(lastSequenceNr() - 1);
                    }
                });
            })
            .match(DeleteMessagesSuccess.class, msg -> {
                log.debug("Delete messages completed.");
            })
            .match(DeleteMessagesFailure.class, msg -> {
                log.error(msg.cause(), "Delete messages failed at offsets " + workers
                    + ", rethrowing");
                throw (Exception) msg.cause();
            })
            .match(WorkerFailure.class, failure -> {
                log.error(failure.cause, "Stream " + failure.worker + " failed at offset "
                    + workers + ", restarting in " + restartDelay);
                onWorkerStopped(failure.worker);
            })
            .match(WorkerDone.class, msg -> {
                log.debug("Completed {}, now offset is: {}", msg.worker, workers);
                onWorkerStopped(msg.worker);
            })
            .matchEquals("reset", msg -> { // for compatibility
                reset();
            })
            .match(Reset.class, msg -> {
                reset();
            })
            .build();
    }

    private void sendProgress() {
        Option<Instant> reimportP = Option.of(reimportProgress.get());
        sender().tell(new Progress(reimportP, workers), self());
    }

    private void createWorker(Instant timestamp, Option<Instant> endTimestamp) {
        if (workers.getIds().size() >= maxWorkerCount) {
            log.warning("Ignoring request to start extra worker at {}, because maximum of {} is already reached.",
                timestamp, workers.getIds().size());
            return;
        }

        persistAndApply(workers.startWorker(timestamp, endTimestamp));
    }

    private void reset() {
        persistAndApply(workers.reset());
    }

    private void persistAndApply(MaterializerActorEvent evt) {
        persist(evt, e -> {
            applyEvent(evt);

            // The first worker will have been stopped and we have a new one at the epoch. Start it.
            workers.getIds().removeAll(workerEndTimestamps.keySet()).forEach(this::materializeEvents);

            sendProgress();
        });
    }

    private void onWorkerStopped(UUID worker) {
        workerEndTimestamps = workerEndTimestamps.remove(worker);
        if (workers.getIds().contains(worker)) {
            context().system().scheduler()
                .scheduleOnce(restartDelay, self(), new StartWorker(worker), context().dispatcher(), self());
        }
    }

    @Override
    public Receive createReceiveRecover() {
        return ReceiveBuilder.create()
            // Backwards-compatibility of old Long.class events from when there was only 1 worker
            .match(Long.class, evt -> workers = workers.applyEvent(evt))
            .match(MaterializerActorEvent.class, this::applyEvent)
            .match(RecoveryCompleted.class, m -> {
                if (workers.isEmpty()) {
                    workers = workers.initialize();
                }
                log.info("Recovery completed, workers: {}", workers);
                workers.getIds().forEach(this::materializeEvents);
            })
            .build();
    }

    private void applyEvent(MaterializerActorEvent evt) {
        workers = workers.applyEvent(evt);
        Seq<UUID> ids = workers.getIds();
        workerEndTimestamps.forEach((id, endTimestamp) -> {
            if (ids.contains(id)) {
                endTimestamp.set(workers.getEndTimestamp(id).map(t -> t.toEpochMilli()).getOrElse(-1L));
            } else {
                // No longer in the list -> we should stop it right now.
                log.info("Worker {} being stopped by event.", id);
                endTimestamp.set(0L);
            }
        });
        recordOffsetMetric();
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

    private void materializeEvents(UUID worker) {
        if (!workers.getIds().contains(worker)) {
            log.debug("Not starting {}", worker);
            return;
        }
        if (workerEndTimestamps.containsKey(worker)) {
            log.warning("Still have timestamp {} for worker {}, about to overwrite.",
                workerEndTimestamps.apply(worker).get(), worker);
        }
        AtomicLong endTimestamp = new AtomicLong(
            workers.getEndTimestamp(worker).map(t -> t.toEpochMilli()).getOrElse(-1L)
        );
        workerEndTimestamps = workerEndTimestamps.put(worker, endTimestamp);
        log.debug("Worker {}: Start materialize of events from {} until (for now) {}",
            worker, workers.getTimestamp(worker).toEpochMilli(), endTimestamp.get());
        recordOffsetMetric();

        loadEvents(workers.getTimestamp(worker))
            .takeWhile(e -> {
                long end = endTimestamp.get();
                if (end == -1) {
                    return true;
                } else {
                    return timestampOf(e).toEpochMilli() < end;
                }
            })
            // get a Seq<E> of where each Seq has the same timestamp, or emit buffer after [rollback]
            // (assuming no events with that timestamp after that)
            .via(GroupWhile.apply((a,b) -> timestampOf(a).equals(timestampOf(b)),
                                  maxEventsPerTimestamp, rollback))

            // Allow multiple timestamps to be processed simultaneously
            .groupedWeightedWithin(updateSize, seq -> (long) seq.size(), Duration.ofSeconds(1))

            // Process them, and emit a single timestamp at t
            .mapAsync(1, listOfSeq ->
                // re-group into batchSize, each one no longer necessarily within one timestamp
                Source.from(Vector.ofAll(listOfSeq).flatMap(seq -> seq))
                    .grouped(batchSize)
                    .mapAsync(1, envelopeList -> materialize(workers.getIds().indexOf(worker), envelopeList))
                    .runWith(Sink.ignore(), materializer)
                    .thenApply(done -> timestampOf(listOfSeq.get(listOfSeq.size() - 1).last()).toEpochMilli())
            )
            //keep highest last offset, in order to limit the amount of events sent to the journal
            .conflate(Long::max)
            .map(t -> new WorkerProgress(worker, Instant.ofEpochMilli(t)))
            .runWith(Sink.actorRefWithAck(self(),
                "init", "ack", new WorkerDone(worker), x -> new WorkerFailure(worker, x)), materializer);
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
                metrics.getReimportRemaining().set(ChronoUnit.MILLIS.between(t, maxTimestamp));
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
        Seq<UUID> ids = workers.getIds();
        for (int i = 0; i < ids.size(); i++) {
            Instant offset = workers.getTimestamp(ids.apply(i));
            metrics.getOffset(i).set(offset.toEpochMilli());
            metrics.getDelay(i).set(Duration.between(offset, Instant.now()).toMillis());
            for (Instant end: workers.getEndTimestamp(workers.getIds().apply(i))) {
                metrics.getRemaining(i).set(Duration.between(offset, end).toMillis());
            }
        }
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
    private CompletionStage<Done> materialize(int workerIndex, java.util.List<E> envelopes) {
        long start = System.nanoTime();
        return CompletableFutures.sequence(
            Vector.ofAll(envelopes)
            .groupBy(this::getConcurrencyKey)
            .values()
            .map(es -> persistSequential(workerIndex, es))
            .map(c -> c.toCompletableFuture())
        ).thenApply(seqOfDone -> {
            long dur = (System.nanoTime() - start) / 1000;
            log.debug("Worker {} materialized {} events in {}ms", workerIndex, envelopes.size(),
                dur / 1000.0);
            if (envelopes.size() > 0) {
                metrics.getMaterializationDuration(workerIndex)
                    .record((long) (dur / 1000.0 / envelopes.size()));
            }
            return Done.getInstance();
        });
    }

    private CompletionStage<Done> persistSequential(int workerIndex, Seq<E> seq) {
        if (seq.isEmpty()) {
            return done;
        } else {
            return materialize(seq.head())
                .thenCompose(done -> {
                    metrics.getEvents(workerIndex).increment();
                    return persistSequential(workerIndex, seq.tail());
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
     * @return the custom tags which will be attached to materializer metrics reported by Kamon.
     * By default, only the class name is attached as a tag in Kamon metrics and there are no custom tags.
     */
    protected Map<String, String> getAdditionalMetricTags() {
        return HashMap.empty();
    }

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
     * Message can be sent to "reset" this materializer, causing it to rematerialize everything.
     *
     * All existing workers will stay at the timestamps that they are, except for the oldest worker,
     * which gets reset to the epoch. In addition, any end timestamps workers have are reset to
     * the start timestamp of the next worker.
     *
     * This way, we queue up rematerialization of all timestamps, while retaining the same amount
     * of workers, and minimizing disruptions in ongoing time sequences.
     *
     * A Progress message is sent back as reply.
     */
    public static class Reset implements Serializable {
        private static final long serialVersionUID = 1L;

        public static final Reset instance = new Reset();
        private Reset() {}
    }

    /**
     * Message that can be sent to this actor to query its current progress.
     *
     * A Progress message will be sent back.
     */
    public static class QueryProgress implements Serializable {
        private static final long serialVersionUID = 1L;

        public static final QueryProgress instance = new QueryProgress();
        private QueryProgress() {}
    }

    public static class Progress implements Serializable {
        private static final long serialVersionUID = 1L;

        private final Option<Instant> reimportTimestamp;
        private final Seq<ProgressWorker> workers;

        private Progress(Option<Instant> reimportTimestamp, MaterializerWorkers state) {
            this.reimportTimestamp = reimportTimestamp;
            this.workers = state.getIds().map(id ->
                new ProgressWorker(id, state.getTimestamp(id), state.getEndTimestamp(id)));
        }

        public Option<Instant> getReimportTimestamp() {
            return reimportTimestamp;
        }

        public Seq<ProgressWorker> getWorkers() {
            return workers;
        }
    }

    public static class ProgressWorker implements Serializable {
        private static final long serialVersionUID = 1L;

        private final UUID id;
        private final Instant timestamp;
        private final Option<Instant> endTimestamp;

        public ProgressWorker(UUID id, Instant timestamp, Option<Instant> endTimestamp) {
            this.id = id;
            this.timestamp = timestamp;
            this.endTimestamp = endTimestamp;
        }

        public UUID getId() {
            return id;
        }

        public Instant getTimestamp() {
            return timestamp;
        }

        public Option<Instant> getEndTimestamp() {
            return endTimestamp;
        }
    }

    /**
     * Message that can be sent to this actor to have it launch an extra worker, which starts
     * work at the given timestamp, and works at most to the given endTimestamp (if given).
     *
     * If the maximum number of workers has been reached, or if the timestamp is too close to an
     * existing worker, the request is ignored.
     *
     * A Progress message is sent back as reply.
     */
    public static class CreateWorker implements Serializable {
        private static final long serialVersionUID = 1L;

        private final Instant timestamp;
        private final Option<Instant> endTimestamp;

        public CreateWorker(Instant timestamp, Option<Instant> endTimestamp) {
            this.timestamp = timestamp;
            this.endTimestamp = endTimestamp;
            for (Instant end: endTimestamp) {
                if (!timestamp.isBefore(end)) {
                    throw new IllegalArgumentException("endTimestamp must be after timestamp if given");
                }
            }
        }
    }

    /** Internal status message, sent from stream to actor every so often. */
    private static class WorkerProgress {
        private final UUID worker;
        private final Instant timestamp;

        public WorkerProgress(UUID worker, Instant timestamp) {
            this.worker = worker;
            this.timestamp = timestamp;
        }
    }

    /** Internal status message, sent from stream to actor when stream fails. */
    private static class WorkerFailure {
        private final UUID worker;
        private final Throwable cause;

        public WorkerFailure(UUID worker, Throwable cause) {
            this.worker = worker;
            this.cause = cause;
        }
    }

    private static class WorkerDone {
        private final UUID worker;

        public WorkerDone(UUID worker) {
            this.worker = worker;
        }
    }

    private static class StartWorker {
        private final UUID worker;

        public StartWorker(UUID worker) {
            this.worker = worker;
        }
    }
}

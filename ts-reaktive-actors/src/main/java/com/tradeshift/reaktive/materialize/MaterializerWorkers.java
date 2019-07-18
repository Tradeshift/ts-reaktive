package com.tradeshift.reaktive.materialize;

import static com.tradeshift.reaktive.protobuf.UUIDs.toJava;
import static com.tradeshift.reaktive.protobuf.UUIDs.toProtobuf;

import java.time.Instant;
import java.time.temporal.TemporalAmount;
import java.util.UUID;
import java.util.function.Function;

import com.tradeshift.reaktive.protobuf.MaterializerActor.MaterializerActorEvent;
import com.tradeshift.reaktive.protobuf.MaterializerActor.MaterializerActorEvent.Worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tradeshift.reaktive.protobuf.UUIDs;

import io.vavr.collection.Seq;
import io.vavr.collection.Vector;
import io.vavr.control.Option;

/**
 * Contains the state of workers for a MaterializerActor, i.e. the timestamp each worker is currently working on, and the
 * timestamp when it's finished.
 **/
public class MaterializerWorkers {
    private static final Logger log = LoggerFactory.getLogger(MaterializerWorkers.class);

    public static MaterializerWorkers empty(TemporalAmount rollback) {
        return new MaterializerWorkers(Vector.empty(), rollback);
    }

    @SafeVarargs
    public static MaterializerWorkers build(TemporalAmount rollback, Function<MaterializerWorkers,MaterializerActorEvent>... f) {
        MaterializerWorkers w = MaterializerWorkers.empty(rollback);
        for (int i = 0; i < f.length; i++) {
            w = w.applyEvent(f[i].apply(w));
        }
        return w;
    }

    private final Seq<Worker> workers;
    private final TemporalAmount rollback;

    /**
     * Creates a new MaterializerWorkers.
     *
     * This constructor is package-private so we can instantiate it directly in unit tests.
     */
    MaterializerWorkers(Seq<Worker> workers, TemporalAmount rollback) {
        this.workers = workers;
        this.rollback = rollback;
    }

    public boolean isEmpty() {
        return workers.isEmpty();
    }

    public MaterializerWorkers initialize() {
        if (isEmpty()) {
            return new MaterializerWorkers(Vector.of(Worker.newBuilder()
                .setId(toProtobuf(UUID.randomUUID()))
                .setTimestamp(0L)
                .build()), rollback);
        } else {
            // already initialized;
            return this;
        }
    }

    public MaterializerWorkers applyEvent(long event) {
        if (workers.size() == 0) {
            return new MaterializerWorkers(Vector.of(Worker.newBuilder()
                .setId(toProtobuf(UUID.randomUUID()))
                .setTimestamp(event)
                .build()), rollback);
        } else if (workers.size() == 1) {
            return new MaterializerWorkers(Vector.of(workers.head().toBuilder()
                .setTimestamp(event)
                .build()), rollback);
        } else {
            throw new IllegalStateException("Encountered legacy Long event " + event
                + " AFTER having more than 1 worker: " + workers);
        }
    }

    public MaterializerWorkers applyEvent(MaterializerActorEvent event) {
        return new MaterializerWorkers(Vector.ofAll(event.getWorkerList()), rollback);
    }

    /**
     * Applies a worker reporting back timestamp progress.
     *
     * @param timestamp The timestamp that the worker has completed processing on.
     *
     * @return An event to emit with the new restart indexes. In case the worker was done, it will
     * be absent from the emitted event.
     */
    // FIXME inject Now() into this as a parameter
    public MaterializerActorEvent onWorkerProgress(UUID workerId, Instant timestamp) {
        int index = workers.map(Worker::getId).indexOf(toProtobuf(workerId));
        if (index == -1) {
            log.warn("Progress for unknown worker: {}, ignoring.", workerId);
            return unchanged();
        }
        Worker worker = workers.apply(index);
        if (worker.hasEndTimestamp() && timestamp.toEpochMilli() >= worker.getEndTimestamp()) {
            return toEvent(workers.removeAt(index));
        } else if (timestamp.toEpochMilli() <= worker.getTimestamp()) {
            // New timestamp is in the past -> ignore it
            return unchanged();
        } else {
            // We're now positively done with timestamp [o].
            // If [o] is long enough ago, we can start with [o+1] next time. Otherwise, we start again at now() minus rollback.
            Instant now = Instant.now();
            long newTimestamp;
            if (timestamp.isBefore(now.minus(rollback))) {
                newTimestamp = timestamp.toEpochMilli() + 1;
            } else {
                newTimestamp = now.minus(rollback).toEpochMilli();
            }

            if (index < workers.size() - 1 && newTimestamp >= workers.apply(index + 1).getTimestamp()) {
                // We're done after all, since we're beyond the next worker's start timestamp
                return toEvent(workers.removeAt(index));
            }

            return toEvent(workers.update(index, worker.toBuilder()
                .setTimestamp(newTimestamp)
                .build()
            ));
        }
    }

    /**
     * Reimport all timestamps, by removing any gaps between workers, and changing the first worker
     * to re-start at zero.
     */
    public MaterializerActorEvent reset() {
        Worker zero = Worker.newBuilder()
            .setId(toProtobuf(UUID.randomUUID()))
            .setTimestamp(0L)
            .build();

        if (workers.size() <= 1) {
            return toEvent(Vector.of(zero));
        } else {
            return toEvent(workers.update(0, zero).sliding(2)
                .map(pair -> pair.apply(0).toBuilder()
                    .setEndTimestamp(pair.apply(1).getTimestamp())
                    .build())
                .toVector()
                .append(workers.last())
            );
        }
    }
    
    private MaterializerActorEvent unchanged() {
        return toEvent(workers);
    }

    private static MaterializerActorEvent toEvent(Iterable<Worker> workers) {
        return MaterializerActorEvent.newBuilder().addAllWorker(workers) .build();
    }

    /**
     * Attempts to start a new worker which should start at the given start timestamp.
     *
     * If the start timestamp is equal to another worker's timestamp, no worker is started.
     *
     * @return An event to emit with new restart indexes.
     */
    public MaterializerActorEvent startWorker(Instant startTimestamp, Option<Instant> endTimestamp) {
        long t = startTimestamp.toEpochMilli();
        if (workers.isEmpty()) {
            return toEvent(Vector.of(Worker.newBuilder()
                .setId(toProtobuf(UUID.randomUUID()))
                .setTimestamp(t)
                .build()
            ));
        }

        if (workers.exists(w -> w.getTimestamp() == t)) {
            // Start timestamp too close to an existing worker, so don't start a new one.
            return unchanged();
        }

        if (endTimestamp.exists(e -> e.toEpochMilli() < t)) {
            // endTimestamp is before start -> don't start this worker
            return unchanged();
        }

        int index = workers.indexWhere(w -> w.getTimestamp() >= t);
        if (index == 0) {
            // Prepend to beginning, run to timestamp of current first worker
            return toEvent(workers
                .insert(0, Worker.newBuilder()
                    .setId(toProtobuf(UUID.randomUUID()))
                    .setTimestamp(t)
                    .setEndTimestamp(earliest(workers.head().getTimestamp(), endTimestamp))
                    .build()
                )
            );
        } else if (index == -1) {
            // Append to end, make currently last worker only run until the timestamp we start at
            return toEvent(workers
                .update(workers.size() - 1, workers.apply(workers.size() - 1).toBuilder()
                    .setEndTimestamp(t)
                    .build()
                )
                .append(Worker.newBuilder()
                    .setId(toProtobuf(UUID.randomUUID()))
                    .setTimestamp(t)
                    .build()
                )
            );
        } else {
            Worker w = workers.apply(index - 1);
            // Workers that are not at the end are guaranteed to have an end timestamp.
            if (w.getEndTimestamp() > t) {
                return toEvent(workers
                    .update(index - 1, w.toBuilder()
                        .setEndTimestamp(t)
                        .build()
                    )
                    .insert(index, Worker.newBuilder()
                        .setId(toProtobuf(UUID.randomUUID()))
                        .setTimestamp(t)
                        .setEndTimestamp(w.getEndTimestamp())
                        .build()
                    )
                );
            } else {
                // no need to adjust end timestamp of previous worker, just insert the new one
                return toEvent(workers
                    .insert(index, Worker.newBuilder()
                        .setId(toProtobuf(UUID.randomUUID()))
                        .setTimestamp(t)
                        .setEndTimestamp(earliest(workers.apply(index).getTimestamp(), endTimestamp))
                        .build()
                    )
                );
            }
        }
    }

    private static long earliest(long a, Option<Instant> b) {
        return (b.isEmpty() || a < b.get().toEpochMilli()) ? a : b.get().toEpochMilli();
    }

    public Seq<UUID> getIds() {
        return workers.map(Worker::getId).map(UUIDs::toJava);
    }

    public Instant getTimestamp(UUID workerId) {
        return Instant.ofEpochMilli(get(workerId).getTimestamp());
    }

    public Option<Instant> getEndTimestamp(UUID workerId) {
        Worker w = get(workerId);
        return Option.when(w.hasEndTimestamp(), () -> Instant.ofEpochMilli(w.getEndTimestamp()));
    }

    private Worker get(UUID workerId) {
        return workers.find(w -> w.getId().equals(toProtobuf(workerId))).getOrElseThrow(() ->
            new IllegalArgumentException("Unknown worker: " + workerId)
        );
    }

    @Override
    public String toString() {
        return workers.map(w -> String.format("%s: %s %s", toJava(w.getId()), Instant.ofEpochMilli(w.getTimestamp()),
            w.hasEndTimestamp() ? "-> " + Instant.ofEpochMilli(w.getEndTimestamp()) : "")).mkString(", \n");
    }
}

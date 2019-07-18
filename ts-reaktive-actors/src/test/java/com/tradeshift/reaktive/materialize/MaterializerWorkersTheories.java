package com.tradeshift.reaktive.materialize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.cuppa.Cuppa.describe;
import static org.forgerock.cuppa.Cuppa.it;
import static org.quicktheories.QuickTheory.qt;
import static org.quicktheories.generators.Generate.constant;
import static org.quicktheories.generators.SourceDSL.integers;
import static org.quicktheories.generators.SourceDSL.longs;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.tradeshift.reaktive.protobuf.MaterializerActor.MaterializerActorEvent;
import com.tradeshift.reaktive.protobuf.MaterializerActor.MaterializerActorEvent.Worker;
import com.tradeshift.reaktive.protobuf.UUIDs;

import org.assertj.core.api.Condition;
import org.forgerock.cuppa.junit.CuppaRunner;
import org.junit.runner.RunWith;
import org.quicktheories.core.Gen;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.Tuple3;
import io.vavr.control.Option;

@RunWith(CuppaRunner.class)
public class MaterializerWorkersTheories {
    {
        describe("MaterializerWorkers.initialize()", () -> {
            it("should always return at least one worker", () -> {
                qt()
                    .forAll(Generators.materializerWorkers())
                    .checkAssert(w -> {
                        MaterializerWorkers i = w.initialize();
                        assertSane(i);
                        assertThat(i.getIds()).isNotEmpty();
                    });
            });

            it("should not create new workers if workers are already present", () -> {
                qt()
                    .forAll(Generators.materializerWorkersNonEmpty())
                    .checkAssert(w -> {
                        MaterializerWorkers i = w.initialize();
                        assertSane(i);
                        assertThat(i.getIds()).hasSameElementsAs(w.getIds());
                    });
            });
        });

        describe("MaterializerWorkers.onWorkerProgress()", () -> {
            it("should stop the worker if needed, and produce sane results", () -> {
                qt()
                    .forAll(Generators.materializerWorkersNonEmpty()
                        .flatMap(w -> constant(w).zip(
                            integers().from(0).upTo(w.getIds().size()),
                            startTimestamps(w),
                            Tuple::of)
                        )
                    )
                    .checkAssert(t -> {
                        MaterializerActorEvent event = t._1.onWorkerProgress(t._1.getIds().apply(t._2), t._3);
                        assertThat(event.getWorkerList()).extracting(w -> UUIDs.toJava(w.getId())).isSubsetOf(t._1.getIds());
                        assertThat(event.getWorkerList().size()).isGreaterThanOrEqualTo(t._1.getIds().size() - 1);

                        MaterializerWorkers p = t._1.applyEvent(event);
                        assertSane(p);
                    });
            });
        });

        describe("MaterializerWorkers.startWorker()", () -> {
            it("should start a worker if needed, and produce sane results", () -> {
                qt()
                    .forAll(Generators.materializerWorkersNonEmpty()
                        .flatMap(w -> constant(w).zip(
                            startTimestamps(w),
                            Generators.option(startTimestamps(w)),
                            Tuple::of)
                        )
                    )
                    .checkAssert(t -> {
                        MaterializerActorEvent event = t._1.startWorker(t._2, t._3);
                        assertThat(event.getWorkerList()).extracting(w -> UUIDs.toJava(w.getId())).containsAll(t._1.getIds());
                        assertThat(event.getWorkerList().size()).isGreaterThanOrEqualTo(t._1.getIds().size());

                        MaterializerWorkers p = t._1.applyEvent(event);
                        assertSane(p);

                        // Applying the same start and end shouldn't create any new workers
                        assertThat(p.startWorker(t._2, t._3)).isEqualTo(event);
                    });
            });

            it("should start a new worker if given a timestamp unequal to existing timestamps", () -> {
                qt()
                    .forAll(Generators.materializerWorkersLong()
                        .flatMap(w ->
                            startAndEndTimestamps(w).map(t -> Tuple.of(w, t._1, t._2))
                        )
                    )
                    .assuming(t ->
                        t._1.getIds().map(t._1::getTimestamp).forAll(
                            time -> time.toEpochMilli() != t._2.toEpochMilli()
                        )
                    )
                    .checkAssert(t -> {
                        MaterializerActorEvent event = t._1.startWorker(t._2, Option.some(t._3));
                        assertThat(event.getWorkerList().size()).isGreaterThan(t._1.getIds().size());
                        assertThat(event.getWorkerList()).haveAtLeastOne(workerForTime(t._2));
                    });
            });
        });

        describe("MaterializerWorkers.reset()", () -> {
            it("should never have gaps in its result", () -> {
                qt()
                    .forAll(Generators.materializerWorkers())
                    .checkAssert(w -> {
                        MaterializerActorEvent event = w.reset();
                        for (int i = 1; i < event.getWorkerCount(); i++) {
                            assertThat(event.getWorker(i-1).getEndTimestamp())
                                .describedAs("Worker %d's end timestamp, in event %s", i, event)
                                .isEqualTo(event.getWorker(i).getTimestamp());

                            assertThat(event.getWorker(i).getTimestamp())
                                .isEqualTo(w.getTimestamp(w.getIds().apply(i)).toEpochMilli());
                        }

                        MaterializerWorkers p = w.applyEvent(event);
                        assertSane(p);
                    });
            });

        });
    }

    private Condition<Worker> workerForTime(Instant time) {
        long t = time.toEpochMilli();
        return new Condition<Worker>(w ->
            w.getTimestamp() <= t && (!w.hasEndTimestamp() || w.getEndTimestamp() >= t)
        , "Contains time " + time);
    }

    private Gen<Instant> startTimestamps(MaterializerWorkers w) {
        return integers().from(0).upTo(w.getIds().size()).zip(longs().from(-100).upTo(100), (idx, ofs) ->
            atLeastEpoch(w.getTimestamp(w.getIds().apply(idx)).plusMillis(ofs))
        );
    }

    private Instant atLeastEpoch(Instant i) {
        return i.isBefore(Instant.EPOCH) ? Instant.EPOCH : i;
    }

    private Gen<Tuple2<Instant,Instant>> startAndEndTimestamps(MaterializerWorkers w) {
        return integers().from(0).upTo(w.getIds().size())
            .zip(longs().from(-5).upTo(100), longs().from(1).upTo(1000), (idx, ofs, dur) -> {
                Instant start = atLeastEpoch(w.getTimestamp(w.getIds().apply(idx)).plusMillis(ofs));
                return Tuple.of(start, start.plusMillis(dur));
            });

    }

    private static void assertSane(MaterializerWorkers w) {
        if (w.getIds().isEmpty()) {
            return;
        };

        assertThat(w.getEndTimestamp(w.getIds().last())).isEmpty();

        for (int i = 1; i < w.getIds().size(); i++) {
            UUID a = w.getIds().apply(i - 1);
            UUID b = w.getIds().apply(i);

            assertThat(w.getEndTimestamp(a)).describedAs("End rimestamp of " + a + " in:\n" + w).isNotEmpty();
            assertThat(w.getEndTimestamp(a).get()).describedAs("End timestamp of " + a + " in:\n" + w).isGreaterThanOrEqualTo(w.getTimestamp(a));
            assertThat(w.getTimestamp(a)).describedAs("Timestamp of " + a + " in:\n" + w).isLessThan(w.getTimestamp(b));
            assertThat(w.getEndTimestamp(a).get()).describedAs("End timestamp of " + a + " in:\n" + w).isLessThanOrEqualTo(w.getTimestamp(b));
        }
    }
}

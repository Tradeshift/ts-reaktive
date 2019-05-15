package com.tradeshift.reaktive.materialize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.cuppa.Cuppa.describe;
import static org.forgerock.cuppa.Cuppa.it;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.forgerock.cuppa.junit.CuppaRunner;
import org.junit.runner.RunWith;

import static io.vavr.control.Option.none;
import static io.vavr.control.Option.some;

@RunWith(CuppaRunner.class)
public class MaterializerWorkersSpec {
    {
        describe("empty MaterializerWorkers", () -> {
            MaterializerWorkers w = MaterializerWorkers.empty(Duration.ofSeconds(6));

            it("should start a new worker and not give it an end timestamp", () -> {
                MaterializerWorkers result = w.applyEvent(w.startWorker(Instant.EPOCH, none()));
                assertThat(result.getIds()).hasSize(1);

                UUID id = result.getIds().head();
                assertThat(result.getTimestamp(id)).isEqualTo(Instant.EPOCH);
                assertThat(result.getEndTimestamp(id)).isEmpty();
            });

            it("should ignore an endTimestamp if given", () -> {
                MaterializerWorkers result = w.applyEvent(w.startWorker(Instant.EPOCH, some(Instant.ofEpochMilli(10000))));
                UUID id = result.getIds().head();
                assertThat(result.getEndTimestamp(id)).isEmpty();
            });
        });

        describe("MaterializerWorkers with 1 worker", () -> {
            MaterializerWorkers w = MaterializerWorkers.build(Duration.ofSeconds(6),
                i -> i.startWorker(Instant.ofEpochMilli(1000000), none()));
            UUID worker1 = w.getIds().head();

            it("should record progress of its worker", () -> {
                MaterializerWorkers result = w.applyEvent(w.onWorkerProgress(worker1, Instant.ofEpochMilli(1500000)));

                assertThat(result.getTimestamp(worker1)).isEqualTo(Instant.ofEpochMilli(1500001));
                assertThat(result.getEndTimestamp(worker1)).isEmpty();
            });

            it("should start a new worker BEFORE the existing timestamp", () -> {
                MaterializerWorkers result = w.applyEvent(w.startWorker(Instant.EPOCH, none()));
                assertThat(result.getIds()).hasSize(2);

                UUID worker2 = result.getIds().head();
                assertThat(worker2).isNotEqualTo(worker1);
                assertThat(result.getTimestamp(worker2)).isEqualTo(Instant.EPOCH);
                assertThat(result.getEndTimestamp(worker2)).contains(Instant.ofEpochMilli(1000000));

                assertThat(result.getTimestamp(worker1)).isEqualTo(Instant.ofEpochMilli(1000000));
                assertThat(result.getEndTimestamp(worker1)).isEmpty();
            });

            it("should honor endTimestamp for a new worker before the existing timestamp", () -> {
                MaterializerWorkers result = w.applyEvent(w.startWorker(Instant.EPOCH, some(Instant.ofEpochMilli(50000))));
                assertThat(result.getIds()).hasSize(2);

                UUID worker2 = result.getIds().head();
                assertThat(worker2).isNotEqualTo(worker1);
                assertThat(result.getTimestamp(worker2)).isEqualTo(Instant.EPOCH);
                assertThat(result.getEndTimestamp(worker2)).contains(Instant.ofEpochMilli(50000));
            });

            it("should cut endTimestamp if it's past the existing worker's timestamp", () -> {
                MaterializerWorkers result = w.applyEvent(w.startWorker(Instant.EPOCH, some(Instant.ofEpochMilli(999999999))));
                assertThat(result.getIds()).hasSize(2);

                UUID worker2 = result.getIds().head();
                assertThat(worker2).isNotEqualTo(worker1);
                assertThat(result.getTimestamp(worker2)).isEqualTo(Instant.EPOCH);
                assertThat(result.getEndTimestamp(worker2)).contains(Instant.ofEpochMilli(1000000));
            });

            it("should not create a new worker if endTimestamp is before the start timestamp", () -> {
                MaterializerWorkers result = w.applyEvent(w.startWorker(Instant.ofEpochMilli(5000), some(Instant.ofEpochMilli(0))));
                assertThat(result.getIds()).containsExactlyElementsOf(w.getIds());
            });

            it("should start a new worker AFTER the existing timestamp", () -> {
                MaterializerWorkers result = w.applyEvent(w.startWorker(Instant.ofEpochMilli(2000000), none()));
                assertThat(result.getIds()).hasSize(2);

                UUID worker2 = result.getIds().last();
                assertThat(worker2).isNotEqualTo(worker1);
                assertThat(result.getTimestamp(worker2)).isEqualTo(Instant.ofEpochMilli(2000000));
                assertThat(result.getEndTimestamp(worker2)).isEmpty();

                assertThat(result.getTimestamp(worker1)).isEqualTo(Instant.ofEpochMilli(1000000));
                assertThat(result.getEndTimestamp(worker1)).contains(Instant.ofEpochMilli(2000000));
            });

            it("should not start a new worker close to the existing timestamp", () -> {
                MaterializerWorkers result = w.applyEvent(w.startWorker(Instant.ofEpochMilli(1000002), none()));
                assertThat(result.getIds()).containsExactly(worker1);
                assertThat(result.getTimestamp(worker1)).isEqualTo(Instant.ofEpochMilli(1000000));
            });
        });

        describe("MaterializerWorkers with 2 workers and a hole between them", () -> {
            MaterializerWorkers w = MaterializerWorkers.build(Duration.ofSeconds(6),
                i -> i.startWorker(Instant.ofEpochMilli(1000000), none()),
                i -> i.startWorker(Instant.ofEpochMilli(2000000), none()),

                // create a third worker between 1 and 2, and complete it immediately. That will create a hole.
                i -> i.startWorker(Instant.ofEpochMilli(1500000), none()),
                i -> i.onWorkerProgress(i.getIds().apply(1), Instant.ofEpochMilli(2000000)));

            UUID worker1 = w.getIds().apply(0);
            UUID worker2 = w.getIds().apply(1);

            it("should have a hole start out with", () -> {
                assertThat(w.getEndTimestamp(worker1)).contains(Instant.ofEpochMilli(1500000));
                assertThat(w.getTimestamp(worker2)).isEqualTo(Instant.ofEpochMilli(2000000));
            });

            it("should stop worker 1 once it catches up with its end timestamp", () -> {
                MaterializerWorkers result = w.applyEvent(w.onWorkerProgress(worker1, Instant.ofEpochMilli(1500000)));

                assertThat(result.getIds()).containsOnly(worker2);
                assertThat(result.getTimestamp(worker2)).isEqualTo(Instant.ofEpochMilli(2000000));
            });

            it("should start a worker between worker 1's end and worker 2's start", () -> {
                MaterializerWorkers result = w.applyEvent(w.startWorker(Instant.ofEpochMilli(1700000), none()));
                assertThat(result.getIds()).hasSize(3);

                UUID worker3 = result.getIds().apply(1);
                assertThat(worker3).isNotEqualTo(worker1).isNotEqualTo(worker2);
                assertThat(result.getTimestamp(worker3)).isEqualTo(Instant.ofEpochMilli(1700000));
                assertThat(result.getEndTimestamp(worker3)).contains(Instant.ofEpochMilli(2000000));

                // worker 1 should be unaffected, because the new worker is started in the hole
                assertThat(result.getTimestamp(worker1)).isEqualTo(Instant.ofEpochMilli(1000000));
                assertThat(result.getEndTimestamp(worker1)).contains(Instant.ofEpochMilli(1500000));
            });

            it("should start a worker between worker 1's start and end", () -> {
                MaterializerWorkers result = w.applyEvent(w.startWorker(Instant.ofEpochMilli(1200000), none()));
                assertThat(result.getIds()).hasSize(3);

                UUID worker3 = result.getIds().apply(1);
                assertThat(worker3).isNotEqualTo(worker1).isNotEqualTo(worker2);
                assertThat(result.getTimestamp(worker3)).isEqualTo(Instant.ofEpochMilli(1200000));
                // New worker should end where worker1 would have ended, since it effectively split worker1 in half.
                assertThat(result.getEndTimestamp(worker3)).contains(Instant.ofEpochMilli(1500000));

                // Worker1 now only needs to run until where the new worker started.
                assertThat(result.getTimestamp(worker1)).isEqualTo(Instant.ofEpochMilli(1000000));
                assertThat(result.getEndTimestamp(worker1)).contains(Instant.ofEpochMilli(1200000));
            });
        });

    }
}

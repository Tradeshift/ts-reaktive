package com.tradeshift.reaktive.materialize;

import static org.quicktheories.QuickTheory.qt;
import static org.quicktheories.generators.SourceDSL.*;
import static org.quicktheories.generators.Generate.constant;

import static com.tradeshift.reaktive.protobuf.UUIDs.toProtobuf;
import com.tradeshift.reaktive.protobuf.MaterializerActor.MaterializerActorEvent.Worker;

import org.quicktheories.core.Gen;

import io.vavr.Tuple;
import io.vavr.collection.Seq;
import io.vavr.collection.Vector;
import io.vavr.control.Option;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAmount;
import java.util.UUID;

public class Generators {
    public static Gen<MaterializerWorkers> materializerWorkers() {
        return integers().from(0).upTo(5).flatMap(count -> materializerWorkers(count, 10));
    }

    public static Gen<MaterializerWorkers> materializerWorkersNonEmpty() {
        return integers().from(1).upTo(5).flatMap(count -> materializerWorkers(count, 10));
    }

    public static Gen<MaterializerWorkers> materializerWorkersLong() {
        return integers().from(1).upTo(5).flatMap(count -> materializerWorkers(count, 99));
    }

    public static Gen<MaterializerWorkers> materializerWorkers(int count, int longWorkerLikelihood) {
        Gen<Integer> gap = integers().from(0).upTo(1000);
        Gen<Integer> work = integers().from(100).upTo(1000).mix(integers().from(100).upTo(1000), longWorkerLikelihood);

        Gen<Seq<Worker>> workersSeq = lists().of(
            work.zip(gap, Tuple::of)
        ).ofSize(count).map(list -> Vector.ofAll(list)).map(data -> {

            Vector<Worker> workers = Vector.empty();

            Vector<Integer> startTimes = data.scanLeft(0, (time, tuple) -> (time + tuple._1 + tuple._2));
            Vector<Integer> endTimes = startTimes.zip(data.map(t -> t._1)).map(t -> t._1 + t._2);

            // All workers except the last one have an end timestamp
            for (int i = 0; i < data.size() - 1; i++) {
                if (endTimes.apply(i) <= startTimes.apply(i)) {
                    throw new IllegalStateException("huh " + startTimes + " / " + endTimes);
                }
                workers = workers.append(Worker.newBuilder()
                    .setId(toProtobuf(workerId(i)))
                    .setTimestamp(startTimes.apply(i))
                    .setEndTimestamp(endTimes.apply(i))
                    .build());
            }

            // The last one always has no timestamp
            if (data.size() > 0) {
                int i = data.size() - 1;
                workers = workers.append(Worker.newBuilder()
                    .setId(toProtobuf(workerId(i)))
                    .setTimestamp(startTimes.apply(i))
                    .build());
            }

            return workers;
        });

        Gen<TemporalAmount> rollbacks = integers().from(0).upTo(1000).map(i -> Duration.of(i, ChronoUnit.SECONDS));

        return workersSeq.zip(rollbacks, MaterializerWorkers::new);
    }

    public static <T> Gen<Option<T>> option(Gen<T> gen) {
        return booleans().all().flatMap(present -> present ? gen.map(Option::some) : constant(Option.none()));
    }

    // Worker UUIDs can't be random, since quickTheories will put in silly and duplicate UUIDs.
    private static UUID workerId(int index) {
        UUID workerIdBase = UUID.fromString("35c2ae01-b26e-49ad-8fd1-000000000000");
        return new UUID(workerIdBase.getMostSignificantBits(), workerIdBase.getLeastSignificantBits() + index);
    }
}

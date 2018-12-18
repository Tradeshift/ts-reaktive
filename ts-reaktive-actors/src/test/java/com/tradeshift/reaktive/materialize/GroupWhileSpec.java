package com.tradeshift.reaktive.materialize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.forgerock.cuppa.Cuppa.describe;
import static org.forgerock.cuppa.Cuppa.it;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.forgerock.cuppa.junit.CuppaRunner;
import org.junit.runner.RunWith;

import com.tradeshift.reaktive.testkit.SharedActorSystemSpec;

import akka.NotUsed;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.testkit.javadsl.TestKit;
import io.vavr.collection.Seq;
import io.vavr.collection.Vector;
import scala.concurrent.duration.FiniteDuration;

@RunWith(CuppaRunner.class)
public class GroupWhileSpec extends SharedActorSystemSpec {
    private static final Flow<Integer, Seq<Integer>, NotUsed> groupWhile =
        Flow.fromGraph(GroupWhile.apply(
            (a, b) -> a.equals(b), 5, FiniteDuration.apply(200, TimeUnit.MILLISECONDS)));

    private Sink<Integer, CompletionStage<List<Seq<Integer>>>> sink = groupWhile.toMat(Sink.seq(), Keep.right());
    private Seq<Seq<Integer>> run(Source<Integer,?> source) {
        try {
            List<Seq<Integer>> result = source.runWith(sink, materializer).toCompletableFuture().get(10, TimeUnit.SECONDS);
            return Vector.ofAll(result);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    private Seq<Seq<Integer>> run(int... args) {
        return run(Source.from(Vector.ofAll(args)));
    }

    {

        describe("GroupWhile", () -> {
            it("should do nothing on an empty stream", () -> {
                assertThat(run()).isEqualTo(Vector.empty());
            });

            it("should fail if upstream fails", () -> {
                assertThatThrownBy(() -> run(Source.failed(new RuntimeException("simulated failure")))).hasMessageContaining("simulated failure");
            });

            it("should emit a single item as a single group", () -> {
                assertThat(run(1)).isEqualTo(Vector.of(Vector.of(1)));
            });

            it("should emit matched items as a single group", () -> {
                assertThat(run(1,1)).isEqualTo(Vector.of(Vector.of(1,1)));
            });

            it("should group subsequent items", () -> {
                assertThat(run(1,2,2,2,3,2,4,4)).isEqualTo(Vector.of(Vector.of(1), Vector.of(2,2,2), Vector.of(3), Vector.of(2), Vector.of(4,4)));
            });

            it("should emit an incomplete group when the idle timeout elapses", () -> {
                TestKit probe = new TestKit(system);
                CompletionStage<Integer> never = new CompletableFuture<>();
                Source.from(Vector.of(1,1)).concat(Source.fromCompletionStage(never)).via(groupWhile).runWith(Sink.actorRef(probe.getRef(), "done"), materializer);

                probe.expectMsg(Vector.of(1,1));
            });

            it("should emit an incomplete subsequent group when the idle timeout elapses", () -> {
                TestKit probe = new TestKit(system);
                CompletionStage<Integer> never = new CompletableFuture<>();
                Source.from(Vector.of(1,2)).concat(Source.fromCompletionStage(never)).via(groupWhile).runWith(Sink.actorRef(probe.getRef(), "done"), materializer);

                probe.expectMsg(Vector.of(1));
                probe.expectMsg(Vector.of(2));
             });

            it("should fail if grouping more than the specified maximum size", () -> {
                assertThatThrownBy(() -> run(1,1,1,1,1,1)).hasMessageContaining("buffer size");
            });
        });
    }
}

package com.tradeshift.reaktive.backup;

import static com.tradeshift.reaktive.backup.DropUntilNext.dropUntilNext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.cuppa.Cuppa.describe;
import static org.forgerock.cuppa.Cuppa.it;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.forgerock.cuppa.junit.CuppaRunner;
import org.junit.runner.RunWith;

import com.tradeshift.reaktive.testkit.SharedActorSystemSpec;

import akka.NotUsed;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

@RunWith(CuppaRunner.class)
public class DropUntilNextSpec extends SharedActorSystemSpec {
    {
        describe("DropUntilNext", () -> {
            final Source<Integer,NotUsed> from1to10to1 = Source.from(Arrays.asList(1,2,3,4,5,6,7,8,9,10,9,8,7,6,5,4,3,2,1));
            
            it("should drop elements until one before the predicate", () -> {
                assertThat(
                    from1to10to1.via(dropUntilNext(i -> i >= 5, false)).runWith(Sink.seq(), materializer).toCompletableFuture().get(1, TimeUnit.SECONDS)
                ).containsExactly(4,5,6,7,8,9,10,9,8,7,6,5,4,3,2,1);
            });
            
            it("should emit the last 2 if the last element matches the predicate", () -> {
                assertThat(
                    from1to10to1.via(dropUntilNext(i -> i >= 10, false)).runWith(Sink.seq(), materializer).toCompletableFuture().get(1, TimeUnit.SECONDS)
                ).containsExactly(9,10,9,8,7,6,5,4,3,2,1);
                
            });
            
            it("should emit nothing if nothing matches the predicate and includeLastIfNoMatch is false", () -> {
                assertThat(
                    Source.range(1,10).via(dropUntilNext(i -> i > 10, false)).runWith(Sink.seq(), materializer).toCompletableFuture().get(1, TimeUnit.SECONDS)
                ).isEmpty();
            });
            
            it("should emit the last element if nothing matches the predicate and includeLastIfNoMatch is true", () -> {
                assertThat(
                    Source.range(1,10).via(dropUntilNext(i -> i > 10, true)).runWith(Sink.seq(), materializer).toCompletableFuture().get(1, TimeUnit.SECONDS)
                ).containsExactly(10);
            });
            
            it("should emit everything if the first element matches the predicate", () -> {
                assertThat(
                    from1to10to1.via(dropUntilNext(i -> i >= 0, false)).runWith(Sink.seq(), materializer).toCompletableFuture().get(1, TimeUnit.SECONDS)
                ).containsExactly(1,2,3,4,5,6,7,8,9,10,9,8,7,6,5,4,3,2,1);
            });
        });
    }
}

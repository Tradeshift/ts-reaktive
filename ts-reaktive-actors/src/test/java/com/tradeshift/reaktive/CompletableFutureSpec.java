package com.tradeshift.reaktive;

import org.forgerock.cuppa.junit.CuppaRunner;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.cuppa.Cuppa.describe;
import static org.forgerock.cuppa.Cuppa.it;

@RunWith(CuppaRunner.class)
public class CompletableFutureSpec {
    {
        describe("CompletableFuture sequence method", () -> {
            it("should return a future that completes when all the futures in the given stream have completed.", () -> {
                Collection<CompletableFuture<Integer>> futures = new ArrayList<>();
                CompletableFuture<Integer> future1 = new CompletableFuture<>();
                CompletableFuture<Integer> future2 = new CompletableFuture<>();
                futures.add(future1);
                futures.add(future2);
                CompletableFuture<List<Number>> sequence = CompletableFutures.sequence(futures);
                future1.complete(1);
                future2.complete(2);
                List<Number> result = sequence.get();
                assertThat(result).hasSize(2);
                assertThat(result.get(0)).isEqualTo(1);
                assertThat(result.get(1)).isEqualTo(2);
            });
        });
    }
}

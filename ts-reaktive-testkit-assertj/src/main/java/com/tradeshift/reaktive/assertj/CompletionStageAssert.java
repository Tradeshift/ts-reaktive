package com.tradeshift.reaktive.assertj;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletionStage;

public class CompletionStageAssert<T> extends AbstractCompletionStageAssert<CompletionStageAssert<T>, T> {
    protected CompletionStageAssert(CompletionStage<T> actual) {
        super(actual.toCompletableFuture(), CompletionStageAssert.class, Duration.of(3, ChronoUnit.SECONDS));
    }
}

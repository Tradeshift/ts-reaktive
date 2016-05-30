package com.tradeshift.reaktive.assertj;

import java.util.concurrent.CompletionStage;

public class CompletionStageAssertions {
    public static <T> CompletionStageAssert<T> assertThat(CompletionStage<T> actual) {
        return new CompletionStageAssert<T>(actual);
    }
}

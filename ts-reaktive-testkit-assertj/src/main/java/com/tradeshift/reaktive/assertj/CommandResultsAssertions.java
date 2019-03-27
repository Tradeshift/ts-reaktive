package com.tradeshift.reaktive.assertj;

import com.tradeshift.reaktive.actors.CommandHandler.Results;

public class CommandResultsAssertions {
    public static <E> CommandResultsAssert<E> assertThat(Results<E> results) {
        return new CommandResultsAssert<>(results);
    }
}

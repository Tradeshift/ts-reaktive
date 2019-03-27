package com.tradeshift.reaktive.assertj;

import com.tradeshift.reaktive.actors.CommandHandler.Results;

public class CommandResultsAssert<E> extends AbstractCommandResultsAssert<CommandResultsAssert<E>,E> {
    public CommandResultsAssert(Results<E> actual) {
        super(actual, CommandResultsAssert.class);
    }

}

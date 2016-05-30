package com.tradeshift.reaktive.assertj;

import static java.lang.String.format;

import java.time.Duration;

import org.assertj.core.error.BasicErrorMessageFactory;

public class FutureShouldComplete extends BasicErrorMessageFactory {
    private FutureShouldComplete(Object future, Duration timeout) {
        super(format("%nExpecting %s to complete within %s.", future.getClass().getSimpleName(), timeout));
    }

    public static FutureShouldComplete shouldCompleteWithin(Object future, Duration timeout) {
        return new FutureShouldComplete(future, timeout);
    }
}

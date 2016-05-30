package com.tradeshift.reaktive.assertj;

import static java.lang.String.format;

import org.assertj.core.error.BasicErrorMessageFactory;

public class FutureShouldFail extends BasicErrorMessageFactory {
    private FutureShouldFail(Object actual) {
        super(format("%nExpected Future to fail, but succeeded instead with %s.", actual));
    }

    public static <T> FutureShouldFail shouldFail(T value) {
        return new FutureShouldFail(value);
    }
}

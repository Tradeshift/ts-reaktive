package com.tradeshift.reaktive.assertj;

import static java.lang.String.format;

import org.assertj.core.error.BasicErrorMessageFactory;

public class FutureShouldSucceed extends BasicErrorMessageFactory {
    private FutureShouldSucceed(String msg, Object... args) {
        super(format(msg, args));
    }

    public static <T> FutureShouldSucceed shouldSucceed(T actual, T expectedValue) {
        return new FutureShouldSucceed("%nExpected Future to succeed with %s, but was %s.", expectedValue, actual);
    }
    
    public static <T> FutureShouldSucceed shouldSucceed(Throwable exception, T expectedValue) {
        return new FutureShouldSucceed("%nExpected Future to succeed with %s, but failed: %s", expectedValue, Throwables.getStackTrace(exception));
    }
    
    public static <T> FutureShouldSucceed shouldSucceed(Throwable exception) {
        return new FutureShouldSucceed("%nExpected Future to succeed, but failed: %s", Throwables.getStackTrace(exception));
    }
}

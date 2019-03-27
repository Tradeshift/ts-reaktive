package com.tradeshift.reaktive.testkit;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import akka.japi.pf.FI;
import io.vavr.CheckedFunction0;

/**
 * Contains utility methods that repeatedly try to evaluate a function, retrying when an 
 * assertion does not (yet) hold.
 */
public class Await {
    private static final long defaultStartInterval = 30;
    private static final long defaultMaxInterval = 500;
    private static final long defaultTimeoutSeconds = 10;
    private static final Within withinDefaultTimeout = new Within(defaultTimeoutSeconds, TimeUnit.SECONDS);
    
    /**
     * Holds a timeout configuration, on which assertion blocks can be repeatedly attempted.
     */
    public static class Within {
        private final long amount;
        private final TimeUnit unit;
        
        private Within(long amount, TimeUnit unit) {
            this.amount = amount;
            this.unit = unit;
        }
        
        /**
         * Repeatedly tries to evaluate the given function, re-trying whenever it fails with
         * an AssertionError. Any other exception will be thrown immediately.
         * @return The final return value of [f], if invoked successfully
         */
        public <T> T eventually(CheckedFunction0<T> f) {
            final long deadline = System.currentTimeMillis() + unit.toMillis(amount);
            long delay = defaultStartInterval;
            AssertionError lastError;
            do {
                try {
                    return f.apply();
                } catch (AssertionError x) {
                    lastError = x;
                    // re-try
                    try { 
                        Thread.sleep(delay); 
                    } catch (InterruptedException y) {
                        throw new RuntimeException(x);
                    }
                    delay = Math.min((long) (delay * 1.2), defaultMaxInterval);
                } catch (RuntimeException x) {
                    throw x;
                } catch (Throwable x) {
                    throw new RuntimeException(x);
                }
            } while (System.currentTimeMillis() < deadline);
            
            throw new AssertionError("Timed out after " + amount + " " + unit + ". Last error: ", lastError);
        }
        
        /**
         * Repeatedly tries to evaluate the given function, re-trying whenever it fails with
         * an AssertionError. Any other exception will be thrown immediately.
         */
        public void eventuallyDo(FI.UnitApplyVoid f) {
            eventually(() -> { 
                f.apply(); 
                return null; 
            });
        }

        /**
         * Waits for the specified future to complete and returns its result, or throw its exception
         * if it has failed (unwrapping any ExecutionException).
         */
        public <T> T result(CompletionStage<T> future) {
            try {
                return future.toCompletableFuture().get(amount, unit);
            } catch (ExecutionException x) {
                Throwable cause = x.getCause();
                if (cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                } else {
                    throw new RuntimeException(cause);
                }
            } catch (RuntimeException x) {
                throw x;
            } catch (Exception x) {
                throw new RuntimeException(x);
            }
        }

        /**
         * Waits for the specified future to fail, and returns its failure exception
         * (unwrapping any ExecutionException).
         *
         * Throws an assertion error if the future succeeds.
         */
        public <T> Throwable failure(CompletionStage<T> future) {
            try {
                T result = future.toCompletableFuture().get(amount, unit);
                throw new AssertionError("Expected future to fail, but instead succeeded with: " + result);
            } catch (ExecutionException x) {
                return x.getCause();
            } catch (Exception x) {
                return x;
            }
        }
    }
    
    /**
     * Prepares to repeatedly attempt a block of assertions. This is part of a DSL; invoke [eventually]
     * on the returned object to actually call a block. For example:
     * 
     *     within(1, SECONDS).eventuallyDo(() -> {
     *         assertThat(myMessage.hasArrived()).isTrue(); 
     *     });
     */
    public static Within within(long amount, TimeUnit unit) {
        return new Within(amount, unit);
    }
    
    /**
     * Repeatedly tries to evaluate the given function, re-trying whenever it fails with
     * an AssertionError. Any other exception will be thrown immediately.
     * 
     * A default timeout of 10 seconds is applied; use within() to use a different timeout.
     * @return The final return value of [f], if invoked successfully
     */
    public static <T> T eventually(CheckedFunction0<T> f) {
        return withinDefaultTimeout.eventually(f);
    }
    
    /**
     * Repeatedly tries to evaluate the given function, re-trying whenever it fails with
     * an AssertionError. Any other exception will be thrown immediately.
     * 
     * A default timeout of 10 seconds is applied; use within() to use a different timeout.
     */
    public static void eventuallyDo(FI.UnitApplyVoid f) {
        withinDefaultTimeout.eventuallyDo(f);
    }    

    /**
     * Waits for the specified future to complete and returns its result, or throw its exception
     * if it has failed (unwrapping any ExecutionException).
     *
     * A default timeout of 10 seconds is applied; use within() to use a different timeout.
     */
    public <T> T result(CompletionStage<T> future) {
        return withinDefaultTimeout.result(future);
    }

    /**
     * Waits for the specified future to fail, and returns its failure exception
     * (unwrapping any ExecutionException).
     *
     * Throws an assertion error if the future succeeds.
     *
     * A default timeout of 10 seconds is applied; use within() to use a different timeout.
     */
    public <T> Throwable failure(CompletionStage<T> future) {
        return withinDefaultTimeout.failure(future);
    }
}

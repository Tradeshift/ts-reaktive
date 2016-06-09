package com.tradeshift.reaktive.testkit;

import java.util.concurrent.TimeUnit;

import akka.japi.pf.FI;
import javaslang.CheckedFunction0;

/**
 * Contains utility methods that repeatedly try to evaluate a function, retrying when an 
 * assertion does not (yet) hold.
 */
public class Await {
    private static final long defaultStartInterval = 30;
    private static final long defaultMaxInterval = 500;
    private static final long defaultTimeoutSeconds = 10;
    
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
     * A default timeout of 10 seconds is applied.
     * @return The final return value of [f], if invoked successfully
     */
    public static <T> T eventually(CheckedFunction0<T> f) {
        return within(defaultTimeoutSeconds, TimeUnit.SECONDS).eventually(f);
    }
    
    /**
     * Repeatedly tries to evaluate the given function, re-trying whenever it fails with
     * an AssertionError. Any other exception will be thrown immediately.
     * 
     * A default timeout of 10 seconds is applied.
     */
    public static void eventuallyDo(FI.UnitApplyVoid f) {
        within(defaultTimeoutSeconds, TimeUnit.SECONDS).eventuallyDo(f);
    }    
}

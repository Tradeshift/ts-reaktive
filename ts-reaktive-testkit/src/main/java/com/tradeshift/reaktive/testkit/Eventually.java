package com.tradeshift.reaktive.testkit;

import akka.japi.pf.FI;
import javaslang.CheckedFunction0;

public class Eventually {
    public static long startInterval = 15;
    public static long maxInterval = 300;
    public static long timeout = 5000;
    
    public static <T> T eventually(CheckedFunction0<T> f) {
        final long deadline = System.currentTimeMillis() + timeout;
        long delay = startInterval;
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
                delay = Math.min((long) (delay * 1.2), maxInterval);
            } catch (RuntimeException x) {
                throw x;
            } catch (Throwable x) {
                throw new RuntimeException(x);
            }
        } while (System.currentTimeMillis() < deadline);
        
        throw new AssertionError("Timed out after " + timeout + "ms. Last error: ", lastError);
    }
    
    public static void eventuallyDo(FI.UnitApplyVoid f) {
        eventually(() -> { f.apply(); return null; });
    }
}

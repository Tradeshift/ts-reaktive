package com.tradeshift.reaktive;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import io.vavr.CheckedFunction0;
import io.vavr.CheckedFunction1;
import io.vavr.CheckedFunction2;
import scala.runtime.AbstractFunction0;
import scala.runtime.AbstractFunction1;

/**
 * Various functions dealing with lambda conversions that are not covered by Java
 */
public class Lambdas {
    /** Like java Consumer, but can throw checked exceptions */
    public interface ConsumerWithException<T, X extends Throwable> {
        public void apply(T t) throws X; 
    }

    /**
     * Removes any thrown exceptions from the signature of the given lambda, while still throwing them.
     * Only safe when you can guarantee that the calling method actually declares the given checked exception.
     */
    public static <T> io.vavr.Function0<T> unchecked(CheckedFunction0<T> f) {
        return () -> { try {
            return f.apply();
        } catch (Throwable x) {
            throwSilently(x);
            return null; // code never reaches here
        }};
    }
    
    /**
     * Removes any thrown exceptions from the signature of the given lambda, while still throwing them.
     * Only safe when you can guarantee that the calling method actually declares the given checked exception.
     */
    public static <T, X extends Throwable> Consumer<T> uncheckedDo(ConsumerWithException<T,X> f) {
        return t -> { try {
            f.apply(t);
        } catch (Throwable x) {
            throwSilently(x);
        }};
    }
    
    /**
     * Removes any thrown exceptions from the signature of the given lambda, while still throwing them.
     * Only safe when you can guarantee that the calling method actually declares the given checked exception.
     */
    public static <A1, T> io.vavr.Function1<A1, T> unchecked(CheckedFunction1<A1, T> f) {
        return a1 -> { try {
            return f.apply(a1);
        } catch (Throwable x) {
            throwSilently(x);
            return null; // code never reaches here
        }};
    }
    
    /**
     * Removes any thrown exceptions from the signature of the given lambda, while still throwing them.
     * Only safe when you can guarantee that the calling method actually declares the given checked exception.
     */
    public static <A1, A2, T> io.vavr.Function2<A1, A2, T> unchecked(CheckedFunction2<A1, A2, T> f) {
        return (a1, a2) -> { try {
            return f.apply(a1, a2);
        } catch (Throwable x) {
            throwSilently(x);
            return null; // code never reaches here
        }};
    }
    
    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void throwSilently(Throwable exception) throws T
    {
        throw (T) exception;
    }
    
    /** Converts the given Supplier to a scala Function0 */
    public static <T> scala.Function0<T> toScala(Supplier<T> f) {
        return new AbstractFunction0<T>() {
            @Override
            public T apply() {
                return f.get();
            }
        };
    }
    
    /** Converts the given Function to a scala Function1 */
    public static <A1, T> scala.Function1<A1,T> toScala(Function<A1, T> f) {
        return new AbstractFunction1<A1,T>() {
            @Override
            public T apply(A1 arg1) {
                return f.apply(arg1);
            }
        };
    }
}

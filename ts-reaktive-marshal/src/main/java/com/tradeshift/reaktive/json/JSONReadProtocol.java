package com.tradeshift.reaktive.json;

import java.util.NoSuchElementException;

import javaslang.Function1;
import javaslang.control.Option;
import javaslang.control.Try;

public abstract class JSONReadProtocol<T> {

    public static final Try<?> NONE = Option.none().toTry();
    
    /**
     * Returns a failed Try that indicates no value was found.
     */
    @SuppressWarnings("unchecked")
    protected static <T> Try<T> none() {
        return (Try<T>) NONE;
    }
    
    public static boolean isNone(Try<?> t) {
        //return t == NONE;
        return t.isFailure() && t.failed().get() instanceof NoSuchElementException;
    }
    
    public interface Reader<T> {        
        Try<T> apply(JSONEvent evt);
        
        Try<T> reset();
        
        public default <U> Reader<U> map(Function1<T,U> f) {
            Reader<T> parent = this;
            return new Reader<U>() {
                @Override
                public Try<U> reset() {
                    return parent.reset().mapTry(f::apply);
                }

                @Override
                public Try<U> apply(JSONEvent evt) {
                    return parent.apply(evt).mapTry(f::apply);
                }
            };
        }
        
        public default <U> Reader<U> flatMap(Function1<T,Try<U>> f) {
            Reader<T> parent = this;
            return new Reader<U>() {
                @Override
                public Try<U> reset() {
                    return parent.reset().flatMap(f);
                }

                @Override
                public Try<U> apply(JSONEvent evt) {
                    return parent.apply(evt).flatMap(f);
                }
            };
        }        
    }
    
    public abstract Reader<T> reader();
    
    /**
     * Returns an appropriate representation of "empty" for this read protocol. By default, returns NONE, which is a failure.
     */
    protected Try<T> empty() {
        return none();
    }
    
    /*
    protected Try<T> combine(Try<T> previous, Try<T> current) {
        // Default strategy for combining:
        //   - If either one is NONE, return the other.
        //   - If either one is a failure, return that (failures aren't ignored)
        //   - Otherwise, return the newest value
        
        if (isNone(previous) && current.isSuccess()) {
            return current;
        } else if (previous.isSuccess() && isNone(current)) {
            return previous;
        } else if (current.isFailure()) {
            return current;
        } else if (previous.isFailure()) {
            return previous;
        } else {
            return current;
        }
    }
    */
}

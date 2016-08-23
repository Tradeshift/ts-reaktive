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
    
    /**
     * Maps the protocol into a different type, invoking [onRead] after reading.
     */
    public <U> JSONReadProtocol<U> map(Function1<T,U> onRead) {
        JSONReadProtocol<T> parent = this;
        return new JSONReadProtocol<U>() {
            @Override
            public Reader<U> reader() {
                return parent.reader().map(onRead);
            }

            @Override
            protected Try<U> empty() {
                return parent.empty().map(onRead);
            }
        };
    };
}

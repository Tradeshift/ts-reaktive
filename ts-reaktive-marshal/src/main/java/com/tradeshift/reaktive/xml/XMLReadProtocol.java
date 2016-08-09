package com.tradeshift.reaktive.xml;

import java.util.NoSuchElementException;

import javax.xml.stream.events.XMLEvent;

import javaslang.Function1;
import javaslang.control.Option;
import javaslang.control.Try;

public abstract class XMLReadProtocol<T> {
    /**
     * An XMLReadProtocol that can produces U always produces a subclass of T.
     */
    @SuppressWarnings("unchecked")
    public static <T, U extends T> XMLReadProtocol<T> narrow(XMLReadProtocol<U> p){
        return (XMLReadProtocol<T>) p;
    }
    
    private static final Try<?> NONE = Option.none().toTry();

    public static abstract class Reader<T> {
        public abstract void reset();
        
        public abstract Try<T> apply(XMLEvent evt);
        
        public <U> Reader<U> map(Function1<T,U> f) {
            Reader<T> parent = this;
            return new Reader<U>() {
                @Override
                public void reset() {
                    parent.reset();
                }

                @Override
                public Try<U> apply(XMLEvent evt) {
                    return parent.apply(evt).mapTry(f::apply);
                }
            };
        }
        
        public <U> Reader<U> flatMap(Function1<T,Try<U>> f) {
            Reader<T> parent = this;
            return new Reader<U>() {
                @Override
                public void reset() {
                    parent.reset();
                }

                @Override
                public Try<U> apply(XMLEvent evt) {
                    Try<U> t = parent.apply(evt).flatMap(f);
                    if (t != NONE && t.isFailure()) {
                        return t.recover(x -> { throw XMLReadException.wrap((RuntimeException) x, evt.getLocation()); });
                    } else {
                        return t;
                    }
                }
            };
        }
    }

    /**
     * Returns a failed Try that indicates no value was found.
     */
    @SuppressWarnings("unchecked")
    protected static <T> Try<T> none() {
        return (Try<T>) NONE;
    }
    
    public static boolean isNone(Try<?> t) {
        return t.isFailure() && t.failed().get() instanceof NoSuchElementException;
    }
    
    /**
     * Returns an appropriate representation of "empty" for this read protocol. 
     */
    protected Try<T> empty() {
        return Try.failure(new NoSuchElementException(toString()));
    }
    
    public abstract Reader<T> reader();
    
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
}

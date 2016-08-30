package com.tradeshift.reaktive.marshal;

import javaslang.Function1;
import javaslang.control.Try;

public interface Reader<E,T> {
    /**
     * A Reader that produces U always produces a subclass of T.
     */
    @SuppressWarnings("unchecked")
    public static <E, T, U extends T> Reader<E,T> narrow(Reader<E,U> p){
        return (Reader<E,T>) p;
    }
    
    Try<T> reset();
    Try<T> apply(E event);
    
    public default <U> Reader<E,U> map(Function1<T,U> f) {
        Reader<E,T> parent = this;
        return new Reader<E,U>() {
            @Override
            public Try<U> reset() {
                return parent.reset().mapTry(f::apply);
            }

            @Override
            public Try<U> apply(E evt) {
                return parent.apply(evt).mapTry(f::apply);
            }
        };
    }
    
    public default <U> Reader<E,U> flatMap(Function1<T,Try<U>> f) {
        Reader<E,T> parent = this;
        return new Reader<E,U>() {
            @Override
            public Try<U> reset() {
                return parent.reset().flatMap(f);
            }

            @Override
            public Try<U> apply(E evt) {
                return parent.apply(evt).flatMap(f);
            }
        };
    }
    
}

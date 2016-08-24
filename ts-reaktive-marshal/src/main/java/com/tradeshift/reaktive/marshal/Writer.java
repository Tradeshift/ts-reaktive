package com.tradeshift.reaktive.marshal;

import java.util.stream.Stream;

import javaslang.Function1;

public interface Writer<E,T> {
    /**
     * Widens a writer of U to become a writer of a superclass of U. This is OK, since a writer that
     * produces U is in fact producing a T.
     */
    @SuppressWarnings("unchecked")
    public static <E, T, U extends T> Writer<E,T> widen(Writer<E,U> w) {
        return (Writer<E,T>) w;
    }
    
    Stream<E> apply(T value);

    public default <U> Writer<E,U> compose(Function1<U,T> f) {
        Writer<E,T> parent = this;
        return new Writer<E,U>() {
            @Override
            public Stream<E> apply(U value) {
                return parent.apply(f.apply(value));
            }
        };
    }
}

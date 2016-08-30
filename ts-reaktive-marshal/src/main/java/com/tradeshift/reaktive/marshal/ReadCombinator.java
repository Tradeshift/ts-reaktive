package com.tradeshift.reaktive.marshal;

public abstract class ReadCombinator<T,U> {
    /**
     * Implementation must return a nested ReadProtocol by invoking inner.mapReader()
     */
    public abstract <E> ReadProtocol<E,U> nest(ReadProtocol<E,T> inner);
}

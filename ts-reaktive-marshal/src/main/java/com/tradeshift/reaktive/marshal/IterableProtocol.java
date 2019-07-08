package com.tradeshift.reaktive.marshal;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import io.vavr.Function1;
import io.vavr.Function2;
import io.vavr.collection.Vector;
import io.vavr.control.Try;

/**
 * Write support for generic Iterable, and read support for mutable collection classes.
 */
public class IterableProtocol<E,T> implements Protocol<E,T>, IterableProtocolMarker {
    /**
     * @param factory Function that creates a new mutable collection with a single value
     * @param add Consumer that adds an element to the collection
     * @param empty Empty collection instance (global, it won't be modified)
     */
    public static <E,T,C extends Iterable<T>> IterableReadProtocol<E,C> read(ReadProtocol<E,T> inner, Supplier<C> factory, BiConsumer<C,T> add) {
        return FoldProtocol.read("*", inner, factory, (c,t) -> {
            add.accept(c, t);
            return c;
        });
    }
    
    public static <E,T, I extends Iterable<T>> IterableWriteProtocol<E,I> write(WriteProtocol<E,T> inner) {
        return new IterableWriteProtocol<>(new WriteProtocol<E,I>() {
            @Override
            public Writer<E,I> writer() {
                return Writer.of(iterable -> {
                    Vector<T> items = Vector.ofAll(iterable);
                    if (items.isEmpty()) {
                        return Vector.empty();
                    } else {
                        Writer<E,T> parentWriter = inner.writer();
                        return items.map(parentWriter::applyAndReset)
                            .flatMap(Function.identity());
                    }
                });
            }
            
            @Override
            public Class<? extends E> getEventType() {
                return inner.getEventType();
            }
            
            @Override
            public String toString() {
                return "*(" + inner + ")";
            }
        });
    }

    public static class IterableReadProtocol<E,T> implements ReadProtocol<E,T>, IterableProtocolMarker {
        private final ReadProtocol<E,T> delegate;

        public IterableReadProtocol(ReadProtocol<E,T> d) { delegate = d; }
        public Reader<E, T> reader() { return delegate.reader(); }
        public Try<T> empty() { return delegate.empty(); }
        public <U> IterableReadProtocol<E,U> map(Function1<T,U> onRead) { return new IterableReadProtocol<>(delegate.map(onRead)); }
    }

    public static class IterableWriteProtocol<E,T> implements WriteProtocol<E,T>, IterableProtocolMarker {
        private final WriteProtocol<E,T> delegate;

        public IterableWriteProtocol(WriteProtocol<E,T> d) { delegate = d; }
        public Class<? extends E> getEventType() { return delegate.getEventType(); }
        public Writer<E,T> writer() { return delegate.writer(); }
        public <U> IterableWriteProtocol<E,U> compose(Function1<U,T> beforeWrite) { return new IterableWriteProtocol<>(delegate.compose(beforeWrite)); }
    }

    public static <E,T> IterableProtocol<E,T> of (IterableReadProtocol<E,T> read, IterableWriteProtocol<E,T> write) {
        return new IterableProtocol<E,T>(Protocol.of(read, write));
    }

    private final Protocol<E,T> delegate;

    public IterableProtocol(Protocol<E,T> d) { delegate = d; }
    public Reader<E, T> reader() { return delegate.reader(); }
    public Try<T> empty() { return delegate.empty(); }
    public <U> IterableReadProtocol<E,U> map(Function1<T,U> onRead) { return new IterableReadProtocol<>(delegate.map(onRead)); }
    public Class<? extends E> getEventType() { return delegate.getEventType(); }
    public Writer<E,T> writer() { return delegate.writer(); }
    public <U> IterableWriteProtocol<E,U> compose(Function1<U,T> beforeWrite) { return new IterableWriteProtocol<>(delegate.compose(beforeWrite)); }
    public <U> IterableProtocol<E,U> map(Function1<T,U> onRead, Function1<U,T> beforeWrite) { return new IterableProtocol<>(delegate.map(onRead, beforeWrite)); }
}

package com.tradeshift.reaktive.marshal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javaslang.Function1;
import javaslang.Function2;
import javaslang.Tuple2;
import javaslang.collection.HashMap;
import javaslang.collection.Map;
import javaslang.collection.Seq;
import javaslang.collection.Vector;
import javaslang.control.Option;
import javaslang.control.Try;

public interface Protocol<E,T> extends ReadProtocol<E,T>, WriteProtocol<E,T> {
    /**
     * Returns a protocol that considers all events emitted results, and vice-versa.
     */
    public static <T> Protocol<T,T> identity(Class<T> type) {
        return new Protocol<T,T>() {
            @Override
            public Reader<T, T> reader() {
                return Reader.identity();
            }

            @Override
            public Class<? extends T> getEventType() {
                return type;
            }

            @Override
            public Writer<T, T> writer() {
                return Writer.identity();
            }
        };
    }
    
    // ----------------------- Alternatives -----------------------------
    
    /**
     * Forwards read events to multiple alternative protocols, emitting whenever any of the alternatives emit. If multiple
     * alternatives emit for the same event, the first one wins.
     */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <E,T> ReadProtocol<E,T> alternatively(ReadProtocol<E,T> first, ReadProtocol<E,T> second, ReadProtocol<E,T>... others) {
        return new AlternativesProtocol<>(Vector.of(first, second).appendAll(Arrays.asList(others)));
    }

    /**
     * Forwards read events to multiple alternative protocols, emitting whenever any of the alternatives emit. If multiple
     * alternatives emit for the same event, the first one wins.
     */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <E,T> Protocol<E,T> alternatively(Protocol<E,T> first, Protocol<E,T> second, Protocol<E,T>... others) {
        return AlternativesProtocol.readWrite(Vector.of(first, second).appendAll(Arrays.asList(others)));
    }

    // ----------------------- Collections -----------------------------
    
    /**
     * Reads an inner protocol multiple times. On reading, creates a {@link javaslang.collection.Vector} to represent it.
     */
    public static <E,T> ReadProtocol<E,Vector<T>> vector(ReadProtocol<E,T> inner) {
        return SeqProtocol.read(inner, Vector.empty());
    }
    
    /**
     * Reads and writes an inner protocol multiple times. On reading, creates a {@link javaslang.collection.Vector} to hold the values.
     * On writing, any {@link javaslang.collection.Seq} will do.
     */
    public static <E,T> Protocol<E,Seq<T>> vector(Protocol<E,T> inner) {
        return of(SeqProtocol.read(inner, Vector.empty()), SeqProtocol.write(inner));
    }

    /**
     * Writes an inner protocol multiple times, represented by a {@link javaslang.collection.Seq}.
     */
    public static <E,T> WriteProtocol<E,Seq<T>> seq(WriteProtocol<E,T> inner) {
        return SeqProtocol.write(inner);
    }

    /**
     * Folds over a repeated nested protocol, merging the results into a single element.
     */
    public static <E,T,U> ReadProtocol<E,U> foldLeft(ReadProtocol<E,T> inner, Supplier<U> initial, Function2<U,T,U> combine) {
        return FoldProtocol.read("*", inner, initial, combine);
    }
    
    /**
     * Invokes the given function for every item the inner protocol emits, while emitting a single null as outer value.
     */
    public static <E,T> ReadProtocol<E,Void> forEach(ReadProtocol<E,T> inner, Consumer<T> consumer) {
        return FoldProtocol.read("*", inner, () -> null, (v1,v2) -> { consumer.accept(v2); return null; });
    }
    
    /**
     * Writes an inner protocol multiple times, represented by a {@link java.util.Iterable}.
     */
    public static <E,T> WriteProtocol<E,Iterable<T>> iterable(WriteProtocol<E,T> inner) {
        return IterableProtocol.write(inner);
    }

    /**
     * Reads an inner protocol multiple times. On reading, creates a {@link java.util.ArrayList} to represent it.
     */
    public static <E,T> ReadProtocol<E,ArrayList<T>> arrayList(ReadProtocol<E,T> inner) {
        return IterableProtocol.read(inner, () -> new ArrayList<>(), java.util.List::add);
    }
    
    /**
     * Reads and writes an inner protocol multiple times. On reading, creates a {@link java.util.ArrayList} to hold the values.
     * On writing, any {@link java.util.List} will do.
     */
    public static <E,T> Protocol<E,java.util.List<T>> arrayList(Protocol<E,T> inner) {
        return of(ReadProtocol.widen(arrayList((ReadProtocol<E,T>)inner)), WriteProtocol.narrow(iterable(inner)));
    }
    
    /**
     * Reads and writes an inner protocol of tuples multiple times. On reading, creates a {@link javaslang.collection.HashMap} to hold the result.
     * On writing, any {@link javaslang.collection.Map} will do.
     */
    public static <E,K,V> Protocol<E,Map<K,V>> hashMap(Protocol<E,Tuple2<K,V>> inner) {
        return of(ReadProtocol.widen(hashMap((ReadProtocol<E,Tuple2<K,V>>) inner)), map(inner));
    }
    
    /**
     * Reads an inner protocol of tuples multiple times. On reading, creates a {@link javaslang.collection.HashMap} to hold the result.
     */
    public static <E,K,V> ReadProtocol<E,HashMap<K,V>> hashMap(ReadProtocol<E,Tuple2<K,V>> inner) {
        return MapProtocol.read(inner, HashMap::of, HashMap.empty());
    }
    
    /**
     * Writes a map using an inner protocol, by turning it into writing multiple tuples.
     */
    public static <E,K,V> WriteProtocol<E,Map<K,V>> map(Protocol<E,Tuple2<K,V>> inner) {
        return MapProtocol.write(inner);
    }
    
    /**
     * Reads and writes a nested protocol optionally, representing it by a {@link javaslang.control.Option}.
     */
    public static <E,T> Protocol<E,Option<T>> option(Protocol<E,T> inner) {
        return of(OptionProtocol.read(inner), OptionProtocol.write(inner));
    }
    
    /**
     * Reads a nested protocol optionally, representing it by a {@link javaslang.control.Option}.
     */
    public static <E,T> ReadProtocol<E,Option<T>> option(ReadProtocol<E,T> inner) {
        return OptionProtocol.read(inner);
    }

    /**
     * Writes a nested protocol optionally, representing it by a {@link javaslang.control.Option}.
     */
    public static <E,T> WriteProtocol<E,Option<T>> option(WriteProtocol<E,T> inner) {
        return OptionProtocol.write(inner);
    }
    
    
    
    // -------------------------------------------------------------------------------------------
    
    /**
     * Combines a read-only and a write-only protocol into a read/write protocol
     */
    public static <E,T> Protocol<E,T> of(ReadProtocol<E,T> read, WriteProtocol<E,T> write) {
        return new Protocol<E,T>() {
            @Override
            public Writer<E,T> writer() {
                return write.writer();
            }

            @Override
            public Class<? extends E> getEventType() {
                return write.getEventType();
            }

            @Override
            public Reader<E,T> reader() {
                return read.reader();
            }
            
            @Override
            public Try<T> empty() {
                return read.empty();
            }
            
            @Override
            public String toString() {
                return read.toString();
            }
        };
    }
    
    /**
     * Maps the protocol into a different type, invoking [onRead] after reading and [beforeWrite] before writing.
     */
    public default <U> Protocol<E,U> map(Function1<T,U> onRead, Function1<U,T> beforeWrite) {
        Protocol<E,T> parent = this;
        return new Protocol<E,U>() {
            @Override
            public Reader<E,U> reader() {
                return parent.reader().map(onRead);
            }

            @Override
            public Writer<E,U> writer() {
                return parent.writer().compose(beforeWrite);
            }
            
            @Override
            public Try<U> empty() {
                return parent.empty().map(onRead);
            }
            
            @Override
            public Class<? extends E> getEventType() {
                return parent.getEventType();
            }
        };
    };
}

package com.tradeshift.reaktive.json;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.tradeshift.reaktive.marshal.StringMarshallable;

import javaslang.Function1;
import javaslang.Function2;
import javaslang.Function3;
import javaslang.Tuple2;
import javaslang.collection.HashMap;
import javaslang.collection.Map;
import javaslang.collection.Seq;
import javaslang.collection.Vector;
import javaslang.control.Option;
import javaslang.control.Try;

@SuppressWarnings("unchecked")
public abstract class JSONProtocol<T> extends JSONReadProtocol<T> implements JSONWriteProtocol<T> {
    public static final StringValueProtocol stringValue = StringValueProtocol.INSTANCE;
    public static final JSONProtocol<Long> longValue = ValueProtocol.LONG;
    public static final JSONProtocol<Integer> integerValue = ValueProtocol.INTEGER;
    
    public static <E> JSONProtocol<E> array(JSONProtocol<E> inner) {
        return new ArrayProtocol<E>(inner);
    }
    
    public static <T> JSONProtocol<T> field(String name, JSONProtocol<T> inner) {
        return FieldProtocol.readWrite(name, inner);
    }
    
    public static <T> JSONReadProtocol<T> field(String name, JSONReadProtocol<T> inner) {
        return FieldProtocol.read(name, inner);
    }
    
    public static <T> JSONWriteProtocol<T> field(String name, JSONWriteProtocol<T> inner) {
        return FieldProtocol.write(name, inner);
    }
    
    /**
     * Converts a JSON protocol that's represented by a String to/from a different type T.
     */
    public static <T> JSONProtocol<T> as(StringMarshallable<T> type, JSONProtocol<String> inner) {
        return new StringMarshallableProtocol<>(type, inner);
    }
    
    public static <T> JSONProtocol<Tuple2<String,T>> anyField(JSONProtocol<T> inner) {
        return AnyFieldProtocol.readWrite(inner);
    }
    
    public static <T> JSONReadProtocol<Tuple2<String,T>> anyField(JSONReadProtocol<T> inner) {
        return AnyFieldProtocol.read(inner);
    }
    
    public static <T> JSONWriteProtocol<Tuple2<String,T>> anyField(JSONWriteProtocol<T> inner) {
        return AnyFieldProtocol.write(inner);
    }
    
    public static <T> JSONReadProtocol<Option<T>> option(JSONReadProtocol<T> inner) {
        return OptionProtocol.read(inner);
    }
    
    public static <T> JSONWriteProtocol<Option<T>> option(JSONWriteProtocol<T> inner) {
        return OptionProtocol.write(inner);
    }
    
    public static <T> JSONProtocol<Option<T>> option(JSONProtocol<T> inner) {
        return OptionProtocol.readWrite(inner);
    }
    
    public static <T> JSONProtocol<Seq<T>> vector(JSONProtocol<T> inner) {
        return SeqProtocol.readWrite(inner, Vector::of, Vector.empty());
    }
    
    public static <T> JSONReadProtocol<Seq<T>> vector(JSONReadProtocol<T> inner) {
        return SeqProtocol.read(inner, Vector::of, Vector.empty());
    }
    
    public static <T> JSONWriteProtocol<Seq<T>> seq(JSONWriteProtocol<T> inner) {
        return SeqProtocol.write(inner);
    }
    
    /**
     * Reads and writes an inner protocol multiple times. On reading, creates a {@link java.util.ArrayList} to hold the values. 
     * On writing, any {@link java.util.List} will do.
     */
    public static <T> JSONProtocol<List<T>> arrayList(JSONProtocol<T> inner) {
        return IterableProtocol.readWrite(inner, new ArrayList<>(), v -> {
            ArrayList<T> l = new ArrayList<>();
            l.add(v);
            return l;
        }, java.util.List::add);
    }
    
    /**
     * Reads an inner protocol multiple times. On reading, creates a {@link java.util.ArrayList} to represent it.
     */
    public static <T> JSONReadProtocol<ArrayList<T>> arrayList(JSONReadProtocol<T> inner) {
        return IterableProtocol.read(inner, new ArrayList<>(), v -> {
            ArrayList<T> l = new ArrayList<>();
            l.add(v);
            return l;
        }, java.util.List::add);
    }
    
    /**
     * Writes an inner protocol multiple times, represented by a {@link java.util.Iterable}.
     */
    public static <T> JSONWriteProtocol<Iterable<T>> iterable(JSONWriteProtocol<T> inner) {
        return IterableProtocol.write(inner);
    }
    
    public static <K,V> JSONProtocol<Map<K,V>> hashMap(JSONProtocol<Tuple2<K,V>> inner) {
        return MapProtocol.readWrite(inner, HashMap::of, HashMap.empty());
    }
    
    public static <K,V> JSONReadProtocol<Map<K,V>> hashMap(JSONReadProtocol<Tuple2<K,V>> inner) {
        return MapProtocol.read(inner, HashMap::of, HashMap.empty());
    }
    
    public static <K,V> JSONWriteProtocol<Map<K,V>> map(JSONWriteProtocol<Tuple2<K,V>> inner) {
        return MapProtocol.write(inner);
    }
    
    /**
     * Forwards read events to multiple alternative protocols, emitting whenever any of the alternatives emit. If multiple 
     * alternatives emit for the same event, the first one wins.
     */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <T> JSONReadProtocol<T> alternatively(JSONReadProtocol<T> first, JSONReadProtocol<T> second, JSONReadProtocol<T>... others) {
        return new AlternativesProtocol<>(Vector.of(first, second).appendAll(Arrays.asList(others)));
    }

    /**
     * Forwards read events to multiple alternative protocols, emitting whenever any of the alternatives emit. If multiple 
     * alternatives emit for the same event, the first one wins.
     */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <T> JSONProtocol<T> alternatively(JSONProtocol<T> first, JSONProtocol<T> second, JSONProtocol<T>... others) {
        return AlternativesProtocol.readWrite(Vector.of(first, second).appendAll(Arrays.asList(others)));
    }

    // ---------------------- object(), 1 type argument ----------------------------------------------
    
    /**
     * Returns a JSON protocol for an object having a single field, typed to the field's type.
     */
    public static <T> ObjectProtocol<T> object(JSONProtocol<T> field) {
        return object(field, Function1.identity(), Function1.identity());
    }
    
    /**
     * Returns a protocol for a JSON object with a single field [p1], using [f] to turn it into a Java object, and [g1] to get the field when writing.
     */
    public static <F1,T> ObjectProtocol<T> object(JSONProtocol<F1> p1, Function1<F1, T> f, Function1<T, F1> g1) { 
        return new ObjectProtocol<T>(Arrays.asList(p1), args -> f.apply((F1) args.get(0)), Arrays.asList(g1));
    }
    
    /**
     * Returns a read-only protocol for a JSON object with a single field [p1], using [f] to turn it into a Java object.
     */
    public static <F1,T> ObjectReadProtocol<T> object(JSONReadProtocol<F1> p1, Function1<F1, T> f) { 
        return new ObjectReadProtocol<T>(Arrays.asList(p1), args -> f.apply((F1) args.get(0)));
    }
    
    /**
     * Returns a write-only protocol for a JSON object with a single field [p1], using [g1] to get the field when writing.
     */
    public static <F1,T> ObjectWriteProtocol<T> object(Function1<T, F1> g1, JSONWriteProtocol<F1> p1) { 
        return new ObjectWriteProtocol<T>(Arrays.asList(p1), Arrays.asList(g1));
    }

    // ---------------------- object(), 2 type arguments ----------------------------------------------
    
    /**
     * Returns a protocol for a JSON object with a fields [p*], using [f] to turn it into a Java object, and [g*] to get the fields when writing.
     */
    public static <F1,F2,T> ObjectProtocol<T> object(JSONProtocol<F1> p1, JSONProtocol<F2> p2, Function2<F1, F2, T> f, Function1<T, F1> g1, Function1<T, F2> g2) { 
        return new ObjectProtocol<T>(Arrays.asList(p1, p2), args -> f.apply((F1) args.get(0), (F2) args.get(1)), Arrays.asList(g1, g2));
    }
    
    /**
     * Returns a read-only protocol for a JSON object with fields [p*], using [f] to turn it into a Java object.
     */
    public static <F1,F2,T> ObjectReadProtocol<T> object(JSONReadProtocol<F1> p1, JSONReadProtocol<F2> p2, Function2<F1, F2, T> f) { 
        return new ObjectReadProtocol<T>(Arrays.asList(p1, p2), args -> f.apply((F1) args.get(0), (F2) args.get(1)));
    }
    
    /**
     * Returns a write-only protocol for a JSON object with fields [p*], using [g*] to get the fields when writing.
     */
    public static <F1,F2,T> ObjectWriteProtocol<T> object(Function1<T, F1> g1, JSONWriteProtocol<F1> p1, Function1<T, F2> g2, JSONWriteProtocol<F2> p2) { 
        return new ObjectWriteProtocol<T>(Arrays.asList(p1, p2), Arrays.asList(g1, g2));
    }
    
    // ---------------------- object(), 3 type arguments ----------------------------------------------
    
    /**
     * Returns a protocol for a JSON object with a fields [p*], using [f] to turn it into a Java object, and [g*] to get the fields when writing.
     */
    public static <F1,F2,F3,T> ObjectProtocol<T> object(JSONProtocol<F1> p1, JSONProtocol<F2> p2, JSONProtocol<F3> p3, Function3<F1, F2, F3, T> f, Function1<T, F1> g1,  Function1<T, F2> g2, Function1<T, F3> g3) { 
        return new ObjectProtocol<T>(Arrays.asList(p1, p2, p3), args -> f.apply((F1) args.get(0), (F2) args.get(1), (F3) args.get(2)), Arrays.asList(g1, g2, g3));
    }
    
    /**
     * Returns a read-only protocol for a JSON object with fields [p*], using [f] to turn it into a Java object.
     */
    public static <F1,F2,F3,T> ObjectReadProtocol<T> object(JSONReadProtocol<F1> p1, JSONReadProtocol<F2> p2, JSONReadProtocol<F3> p3, Function3<F1, F2, F3, T> f) { 
        return new ObjectReadProtocol<T>(Arrays.asList(p1, p2, p3), args -> f.apply((F1) args.get(0), (F2) args.get(1), (F3) args.get(2)));
    }
    
    /**
     * Returns a write-only protocol for a JSON object with fields [p*], using [g*] to get the fields when writing.
     */
    public static <F1,F2,F3,T> ObjectWriteProtocol<T> object(JSONWriteProtocol<F1> p1, Function1<T, F1> g1, JSONWriteProtocol<F2> p2, Function1<T, F2> g2, JSONWriteProtocol<F3> p3, Function1<T, F3> g3) { 
        return new ObjectWriteProtocol<T>(Arrays.asList(p1, p2, p3), Arrays.asList(g1, g2, g3));
    }
    
    // --------------------------------------------------------------------
    
    /**
     * Maps the protocol into a different type, invoking [onRead] after reading and [beforeWrite] before writing.
     */
    public <U> JSONProtocol<U> map(Function1<T,U> onRead, Function1<U,T> beforeWrite) {
        JSONProtocol<T> parent = this;
        return new JSONProtocol<U>() {
            @Override
            public Reader<U> reader() {
                return parent.reader().map(onRead);
            }

            @Override
            public Writer<U> writer() {
                return parent.writer().compose(beforeWrite);
            }
            
            @Override
            protected Try<U> empty() {
                return parent.empty().map(onRead);
            }
            
            @Override
            public boolean isEmpty(U value) {
                return parent.isEmpty(beforeWrite.apply(value));
            }
        };
    };
}

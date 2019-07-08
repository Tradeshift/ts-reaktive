package com.tradeshift.reaktive.json;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;

import com.tradeshift.reaktive.marshal.IterableProtocol;
import com.tradeshift.reaktive.marshal.IterableProtocolMarker;
import com.tradeshift.reaktive.marshal.Locator;
import com.tradeshift.reaktive.marshal.Protocol;
import static com.tradeshift.reaktive.marshal.Protocol.iterable;
import static com.tradeshift.reaktive.marshal.Protocol.option;
import static com.tradeshift.reaktive.marshal.Protocol.vector;
import com.tradeshift.reaktive.marshal.ReadProtocol;
import com.tradeshift.reaktive.marshal.StringProtocol;
import com.tradeshift.reaktive.marshal.WriteProtocol;
import com.tradeshift.reaktive.marshal.IterableProtocol.IterableReadProtocol;
import com.tradeshift.reaktive.marshal.IterableProtocol.IterableWriteProtocol;

import io.vavr.Function1;
import io.vavr.Function2;
import io.vavr.Function3;
import io.vavr.Function4;
import io.vavr.Function5;
import io.vavr.Tuple2;
import io.vavr.collection.Vector;
import io.vavr.collection.Seq;
import io.vavr.control.Option;

@SuppressWarnings("unchecked")
public class JSONProtocol<T> {
    public static final Locator<JSONEvent> locator = evt -> ""; // TODO location reporting for JSON events
    
    public static final StringProtocol<JSONEvent> stringValue = StringValueProtocol.INSTANCE;
    
    public static final Protocol<JSONEvent,Long> longValue = ValueProtocol.LONG;
    public static final Protocol<JSONEvent,Integer> integerValue = ValueProtocol.INTEGER;
    public static final Protocol<JSONEvent,BigInteger> bigIntegerValue = ValueProtocol.BIGINTEGER;
    public static final Protocol<JSONEvent,BigDecimal> bigDecimalValue = ValueProtocol.BIGDECIMAL;
    public static final Protocol<JSONEvent,Boolean> booleanValue = ValueProtocol.BOOLEAN;

    public static <T> WriteProtocol<JSONEvent, Iterable<T>> optionalIterableField(String name, WriteProtocol<JSONEvent,T> inner) {
        return option(
            field(name,
                array(
                    iterable(inner)
                )
            )
        ).compose((Iterable<T> i) -> Option.when(i.iterator().hasNext(), i));
    }

    public static <T> ReadProtocol<JSONEvent, Vector<T>> optionalVectorField(String name, ReadProtocol<JSONEvent,T> inner) {
        return option(
            field(name,
                array(
                    vector(inner)
                )
            )
        ).map(o -> o.getOrElse(Vector.empty()));
    }
    
    public static <T> Protocol<JSONEvent, Seq<T>> optionalVectorField(String name, Protocol<JSONEvent,T> inner) {
        return option(
            field(name,
                array(
                    vector(inner)
                )
            )
        ).map(o -> o.getOrElse(Vector.empty()), (Seq<T> i) -> Option.when(!i.isEmpty(), i));
    }

    public static <E> Protocol<JSONEvent, E> array(Protocol<JSONEvent, E> inner) {
        return Protocol.of(ArrayProtocol.read(inner), ArrayProtocol.write(inner));
    }
    
    public static <E> ReadProtocol<JSONEvent, E> array(ReadProtocol<JSONEvent, E> inner) {
        return ArrayProtocol.read(inner);
    }
    
    public static <E> WriteProtocol<JSONEvent, E> array(WriteProtocol<JSONEvent, E> inner) {
        return ArrayProtocol.write(inner);
    }

    @Deprecated
    /** @deprecated Iterable protocols can not occur as field body, since there must be exactly 1 value there. */
    public static <T> void field(String name, IterableProtocol<JSONEvent, T> inner) {
        throw new UnsupportedOperationException("Iterable protocols can not occur as field body, since there must be exactly 1 value there.");
    }
    @Deprecated
    /** @deprecated Iterable protocols can not occur as field body, since there must be exactly 1 value there. */
    public static <T> void field(String name, IterableReadProtocol<JSONEvent, T> inner) {
        throw new UnsupportedOperationException("Iterable protocols can not occur as field body, since there must be exactly 1 value there.");
    }
    @Deprecated
    /** @deprecated Iterable protocols can not occur as field body, since there must be exactly 1 value there. */
    public static <T> void field(String name, IterableWriteProtocol<JSONEvent, T> inner) {
        throw new UnsupportedOperationException("Iterable protocols can not occur as field body, since there must be exactly 1 value there.");
    }

    public static <T> Protocol<JSONEvent, T> field(String name, Protocol<JSONEvent, T> inner) {
        if (inner instanceof IterableProtocolMarker) {
            throw new UnsupportedOperationException("Iterable protocols can not occur as field body, since there must be exactly 1 value there.");
        }
        return Protocol.of(FieldProtocol.read(name, inner), FieldProtocol.write(name, inner));
    }
    
    public static <T> ReadProtocol<JSONEvent, T> field(String name, ReadProtocol<JSONEvent, T> inner) {
        if (inner instanceof IterableProtocolMarker) {
            throw new UnsupportedOperationException("Iterable protocols can not occur as field body, since there must be exactly 1 value there.");
        }
        return FieldProtocol.read(name, inner);
    }
    
    public static <T> WriteProtocol<JSONEvent, T> field(String name, WriteProtocol<JSONEvent, T> inner) {
        if (inner instanceof IterableProtocolMarker) {
            throw new UnsupportedOperationException("Iterable protocols can not occur as field body, since there must be exactly 1 value there.");
        }
        return FieldProtocol.write(name, inner);
    }
    
    public static <T> Protocol<JSONEvent, Tuple2<String,T>> anyField(Protocol<JSONEvent, T> inner) {
        return Protocol.of(AnyFieldProtocol.read(inner), AnyFieldProtocol.write(inner));
    }
    
    public static <T> ReadProtocol<JSONEvent, Tuple2<String,T>> anyField(ReadProtocol<JSONEvent, T> inner) {
        return AnyFieldProtocol.read(inner);
    }
    
    public static <T> WriteProtocol<JSONEvent, Tuple2<String,T>> anyField(WriteProtocol<JSONEvent, T> inner) {
        return AnyFieldProtocol.write(inner);
    }
            
    // ---------------------- object(), 1 type argument ----------------------------------------------
    
    /**
     * Returns a JSON protocol for an object having a single field, typed to the field's type.
     */
    public static <T> ObjectProtocol<T> object(Protocol<JSONEvent, T> field) {
        return new ObjectProtocol<>(field);
    }
    
    /**
     * Returns a JSON protocol for reading an object having a single field, typed to the field's type.
     */
    public static <T> ObjectReadProtocol<T> object(ReadProtocol<JSONEvent, T> field) {
        return new ObjectReadProtocol<>(field);
    }
    
    /**
     * Returns a JSON protocol for writing an object having a single field, typed to the field's type.
     */
    public static <T> ObjectWriteProtocol<T> object(WriteProtocol<JSONEvent, T> field) {
        return new ObjectWriteProtocol<>(field);
    }
    
    /**
     * Returns a protocol for a JSON object with a single field [p1], using [f] to turn it into a Java object, and [g1] to get the field when writing.
     */
    public static <F1,T> ObjectProtocol<T> object(Protocol<JSONEvent, F1> p1, Function1<F1, T> f, Function1<T, F1> g1) {
        return new ObjectProtocol<>(Arrays.asList(p1), args -> f.apply((F1) args.get(0)), Arrays.asList(g1));
    }
    
    /**
     * Returns a read-only protocol for a JSON object with a single field [p1], using [f] to turn it into a Java object.
     */
    public static <F1,T> ObjectReadProtocol<T> object(ReadProtocol<JSONEvent, F1> p1, Function1<F1, T> f) {
        return new ObjectReadProtocol<>(Arrays.asList(p1), args -> f.apply((F1) args.get(0)));
    }
    
    /**
     * Returns a write-only protocol for a JSON object with a single field [p1], using [g1] to get the field when writing.
     */
    public static <F1,T> ObjectWriteProtocol<T> object(Function1<T, F1> g1, WriteProtocol<JSONEvent, F1> p1) {
        return new ObjectWriteProtocol<>(Arrays.asList(p1), Arrays.asList(g1));
    }

    // ---------------------- object(), 2 type arguments ----------------------------------------------
    
    /**
     * Returns a protocol for a JSON object with a fields [p*], using [f] to turn it into a Java object, and [g*] to get the fields when writing.
     */
    public static <F1,F2,T> ObjectProtocol<T> object(Protocol<JSONEvent, F1> p1, Protocol<JSONEvent, F2> p2, Function2<F1, F2, T> f, Function1<T, F1> g1, Function1<T, F2> g2) {
        return new ObjectProtocol<>(Arrays.asList(p1, p2), args -> f.apply((F1) args.get(0), (F2) args.get(1)), Arrays.asList(g1, g2));
    }
    
    /**
     * Returns a read-only protocol for a JSON object with fields [p*], using [f] to turn it into a Java object.
     */
    public static <F1,F2,T> ObjectReadProtocol<T> object(ReadProtocol<JSONEvent, F1> p1, ReadProtocol<JSONEvent, F2> p2, Function2<F1, F2, T> f) {
        return new ObjectReadProtocol<>(Arrays.asList(p1, p2), args -> f.apply((F1) args.get(0), (F2) args.get(1)));
    }
    
    /**
     * Returns a write-only protocol for a JSON object with fields [p*], using [g*] to get the fields when writing.
     */
    public static <F1,F2,T> ObjectWriteProtocol<T> object(Function1<T, F1> g1, WriteProtocol<JSONEvent, F1> p1, Function1<T, F2> g2, WriteProtocol<JSONEvent, F2> p2) {
        return new ObjectWriteProtocol<>(Arrays.asList(p1, p2), Arrays.asList(g1, g2));
    }
    
    // ---------------------- object(), 3 type arguments ----------------------------------------------
    
    /**
     * Returns a protocol for a JSON object with a fields [p*], using [f] to turn it into a Java object, and [g*] to get the fields when writing.
     */
    public static <F1,F2,F3,T> ObjectProtocol<T> object(Protocol<JSONEvent, F1> p1, Protocol<JSONEvent, F2> p2, Protocol<JSONEvent, F3> p3, Function3<F1, F2, F3, T> f, Function1<T, F1> g1,  Function1<T, F2> g2, Function1<T, F3> g3) {
        return new ObjectProtocol<>(Arrays.asList(p1, p2, p3), args -> f.apply((F1) args.get(0), (F2) args.get(1), (F3) args.get(2)), Arrays.asList(g1, g2, g3));
    }
    
    /**
     * Returns a read-only protocol for a JSON object with fields [p*], using [f] to turn it into a Java object.
     */
    public static <F1,F2,F3,T> ObjectReadProtocol<T> object(ReadProtocol<JSONEvent, F1> p1, ReadProtocol<JSONEvent, F2> p2, ReadProtocol<JSONEvent, F3> p3, Function3<F1, F2, F3, T> f) {
        return new ObjectReadProtocol<>(Arrays.asList(p1, p2, p3), args -> f.apply((F1) args.get(0), (F2) args.get(1), (F3) args.get(2)));
    }
    
    /**
     * Returns a write-only protocol for a JSON object with fields [p*], using [g*] to get the fields when writing.
     */
    public static <F1,F2,F3,T> ObjectWriteProtocol<T> object(Function1<T, F1> g1, WriteProtocol<JSONEvent, F1> p1, Function1<T, F2> g2, WriteProtocol<JSONEvent, F2> p2, Function1<T, F3> g3, WriteProtocol<JSONEvent, F3> p3) {
        return new ObjectWriteProtocol<>(Arrays.asList(p1, p2, p3), Arrays.asList(g1, g2, g3));
    }

    // ---------------------- object(), 4 type arguments ----------------------------------------------

    /**
     * Returns a protocol for a JSON object with a fields [p*], using [f] to turn it into a Java object, and [g*] to get the fields when writing.
     */
    public static <F1, F2, F3, F4, T> ObjectProtocol<T> object(Protocol<JSONEvent, F1> p1, Protocol<JSONEvent, F2> p2, Protocol<JSONEvent, F3> p3, Protocol<JSONEvent, F4> p4, Function4<F1, F2, F3, F4, T> f, Function1<T, F1> g1, Function1<T, F2> g2, Function1<T, F3> g3, Function1<T, F4> g4) {
        return new ObjectProtocol<>(Arrays.asList(p1, p2, p3, p4), args -> f.apply((F1) args.get(0), (F2) args.get(1), (F3) args.get(2), (F4) args.get(3)), Arrays.asList(g1, g2, g3, g4));
    }

    /**
     * Returns a read-only protocol for a JSON object with fields [p*], using [f] to turn it into a Java object.
     */
    public static <F1, F2, F3, F4, T> ObjectReadProtocol<T> object(ReadProtocol<JSONEvent, F1> p1, ReadProtocol<JSONEvent, F2> p2, ReadProtocol<JSONEvent, F3> p3, ReadProtocol<JSONEvent, F4> p4, Function4<F1, F2, F3, F4, T> f) {
        return new ObjectReadProtocol<>(Arrays.asList(p1, p2, p3, p4), args -> f.apply((F1) args.get(0), (F2) args.get(1), (F3) args.get(2), (F4) args.get(3)));
    }

    /**
     * Returns a write-only protocol for a JSON object with fields [p*], using [g*] to get the fields when writing.
     */
    public static <F1, F2, F3, F4, T> ObjectWriteProtocol<T> object(Function1<T, F1> g1, WriteProtocol<JSONEvent, F1> p1, Function1<T, F2> g2, WriteProtocol<JSONEvent, F2> p2, Function1<T, F3> g3, WriteProtocol<JSONEvent, F3> p3, Function1<T, F4> g4, WriteProtocol<JSONEvent, F4> p4) {
        return new ObjectWriteProtocol<>(Arrays.asList(p1, p2, p3, p4), Arrays.asList(g1, g2, g3, g4));
    }

    // ---------------------- object(), 5 type arguments ----------------------------------------------

    /**
     * Returns a protocol for a JSON object with a fields [p*], using [f] to turn it into a Java object, and [g*] to get the fields when writing.
     */
    public static <F1, F2, F3, F4, F5, T> ObjectProtocol<T> object(Protocol<JSONEvent, F1> p1, Protocol<JSONEvent, F2> p2, Protocol<JSONEvent, F3> p3, Protocol<JSONEvent, F4> p4, Protocol<JSONEvent, F5> p5, Function5<F1, F2, F3, F4, F5, T> f, Function1<T, F1> g1, Function1<T, F2> g2, Function1<T, F3> g3, Function1<T, F4> g4, Function1<T, F5> g5) {
        return new ObjectProtocol<>(Arrays.asList(p1, p2, p3, p4, p5), args -> f.apply((F1) args.get(0), (F2) args.get(1), (F3) args.get(2), (F4) args.get(3), (F5) args.get(4)), Arrays.asList(g1, g2, g3, g4, g5));
    }

    /**
     * Returns a read-only protocol for a JSON object with fields [p*], using [f] to turn it into a Java object.
     */
    public static <F1, F2, F3, F4, F5, T> ObjectReadProtocol<T> object(ReadProtocol<JSONEvent, F1> p1, ReadProtocol<JSONEvent, F2> p2, ReadProtocol<JSONEvent, F3> p3, ReadProtocol<JSONEvent, F4> p4, ReadProtocol<JSONEvent, F5> p5, Function5<F1, F2, F3, F4, F5, T> f) {
        return new ObjectReadProtocol<>(Arrays.asList(p1, p2, p3, p4, p5), args -> f.apply((F1) args.get(0), (F2) args.get(1), (F3) args.get(2), (F4) args.get(3), (F5) args.get(4)));
    }

    /**
     * Returns a write-only protocol for a JSON object with fields [p*], using [g*] to get the fields when writing.
     */
    public static <F1, F2, F3, F4, F5, T> ObjectWriteProtocol<T> object(Function1<T, F1> g1, WriteProtocol<JSONEvent, F1> p1, Function1<T, F2> g2, WriteProtocol<JSONEvent, F2> p2, Function1<T, F3> g3, WriteProtocol<JSONEvent, F3> p3, Function1<T, F4> g4, WriteProtocol<JSONEvent, F4> p4, Function1<T, F5> g5, WriteProtocol<JSONEvent, F5> p5) {
        return new ObjectWriteProtocol<>(Arrays.asList(p1, p2, p3, p4, p5), Arrays.asList(g1, g2, g3, g4, g5));
    }
    
    // --------------------------------------------------------------------
}

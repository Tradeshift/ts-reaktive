package com.tradeshift.reaktive.json;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;

import com.tradeshift.reaktive.marshal.Locator;
import com.tradeshift.reaktive.marshal.Protocol;
import com.tradeshift.reaktive.marshal.ReadProtocol;
import com.tradeshift.reaktive.marshal.StringProtocol;
import com.tradeshift.reaktive.marshal.WriteProtocol;

import javaslang.Function1;
import javaslang.Function2;
import javaslang.Function3;
import javaslang.Tuple2;

@SuppressWarnings("unchecked")
public class JSONProtocol<T> {
    public static final Locator<JSONEvent> locator = evt -> ""; // TODO location reporting for JSON events
    
    public static final StringProtocol<JSONEvent> stringValue = StringValueProtocol.INSTANCE;
    
    public static final Protocol<JSONEvent,Long> longValue = ValueProtocol.LONG;
    public static final Protocol<JSONEvent,Integer> integerValue = ValueProtocol.INTEGER;
    public static final Protocol<JSONEvent,BigInteger> bigIntegerValue = ValueProtocol.BIGINTEGER;
    public static final Protocol<JSONEvent,BigDecimal> bigDecimalValue = ValueProtocol.BIGDECIMAL;
    public static final Protocol<JSONEvent,Boolean> booleanValue = ValueProtocol.BOOLEAN;

    public static <E> Protocol<JSONEvent, E> array(Protocol<JSONEvent, E> inner) {
        return Protocol.of(ArrayProtocol.read(inner), ArrayProtocol.write(inner));
    }
    
    public static <E> ReadProtocol<JSONEvent, E> array(ReadProtocol<JSONEvent, E> inner) {
        return ArrayProtocol.read(inner);
    }
    
    public static <E> WriteProtocol<JSONEvent, E> array(WriteProtocol<JSONEvent, E> inner) {
        return ArrayProtocol.write(inner);
    }
    
    public static <T> Protocol<JSONEvent, T> field(String name, Protocol<JSONEvent, T> inner) {
        return Protocol.of(FieldProtocol.read(name, inner), FieldProtocol.write(name, inner));
    }
    
    public static <T> ReadProtocol<JSONEvent, T> field(String name, ReadProtocol<JSONEvent, T> inner) {
        return FieldProtocol.read(name, inner);
    }
    
    public static <T> WriteProtocol<JSONEvent, T> field(String name, WriteProtocol<JSONEvent, T> inner) {
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
    public static <F1,F2,F3,T> ObjectWriteProtocol<T> object(WriteProtocol<JSONEvent, F1> p1, Function1<T, F1> g1, WriteProtocol<JSONEvent, F2> p2, Function1<T, F2> g2, WriteProtocol<JSONEvent, F3> p3, Function1<T, F3> g3) {
        return new ObjectWriteProtocol<>(Arrays.asList(p1, p2, p3), Arrays.asList(g1, g2, g3));
    }
    
    // --------------------------------------------------------------------
}

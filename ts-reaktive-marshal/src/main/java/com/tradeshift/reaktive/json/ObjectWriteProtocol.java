package com.tradeshift.reaktive.json;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import com.tradeshift.reaktive.marshal.ConstantProtocol;
import com.tradeshift.reaktive.marshal.WriteProtocol;
import com.tradeshift.reaktive.marshal.Writer;

import javaslang.Function1;
import javaslang.collection.Seq;
import javaslang.collection.Vector;

/**
 * Generic class to combine several nested FieldProtocols into reading/writing a Java object instance.  
 */
@SuppressWarnings("unchecked")
public class ObjectWriteProtocol<T> implements WriteProtocol<JSONEvent, T> {
    private final Seq<WriteProtocol<JSONEvent, ?>> protocols;
    private final Writer<JSONEvent, T> writer;
    private final Seq<WriteProtocol<JSONEvent, ConstantProtocol.Present>> conditions;
    private final List<Function1<T, ?>> getters;

    public ObjectWriteProtocol(
        List<WriteProtocol<JSONEvent, ?>> protocols,
        List<Function1<T, ?>> getters
    ) {
        this(Vector.ofAll(protocols), getters, Vector.empty());
    }
    
    ObjectWriteProtocol(
        Seq<WriteProtocol<JSONEvent, ?>> protocols,
        List<Function1<T, ?>> getters,
        Seq<WriteProtocol<JSONEvent, ConstantProtocol.Present>> conditions
    ) {
        this.protocols = protocols;
        this.getters = getters;
        this.conditions = conditions;
        if (protocols.size() != getters.size()) {
            throw new IllegalArgumentException("protocols must match getters");
        }
        
        this.writer = value -> Stream.of(
            Stream.of(JSONEvent.START_OBJECT),
            
            // write out constant-valued conditions
            conditions.map(c -> c.writer().apply(ConstantProtocol.PRESENT)).toJavaStream().flatMap(Function.identity()),
            
            // write out actual fields
            Stream.iterate(0, i -> i + 1).limit(protocols.size()).map(i -> {
                Writer<JSONEvent, Object> w = (Writer<JSONEvent, Object>) protocols.get(i).writer();
                return w.apply(getters.get(i).apply(value));
            }).flatMap(Function.identity()),
            
            Stream.of(JSONEvent.END_OBJECT)
        ).flatMap(Function.identity());
    }
    
    @Override
    public Class<? extends JSONEvent> getEventType() {
        return JSONEvent.class;
    }

    @Override
    public Writer<JSONEvent, T> writer() {
        return writer;
    }
    
    /**
     * Returns a new protocol that, in addition, writes out the given value when serializing.
     */
    public <U> ObjectWriteProtocol<T> having(WriteProtocol<JSONEvent, U> nestedProtocol, U value) {
        return new ObjectWriteProtocol<T>(protocols, getters, conditions.append(ConstantProtocol.write(nestedProtocol, value)));
    }
    
    @Override
    public String toString() {
        return "object(" + protocols.mkString(",");
    }
}
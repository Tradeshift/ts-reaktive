package com.tradeshift.reaktive.json;

import java.util.List;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger log = LoggerFactory.getLogger(ObjectWriteProtocol.class);
    
    private final Seq<WriteProtocol<JSONEvent, ?>> protocols;
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
    }
    
    @Override
    public Class<? extends JSONEvent> getEventType() {
        return JSONEvent.class;
    }

    @Override
    public Writer<JSONEvent, T> writer() {
        return new Writer<JSONEvent, T>() {
            boolean started = false;

            @Override
            public Seq<JSONEvent> apply(T value) {
                log.debug("{}: Writing {}", ObjectWriteProtocol.this, value);
                
                Seq<JSONEvent> prefix = (started) ? Vector.empty() : Vector.of(JSONEvent.START_OBJECT);
                
                started = true;
                return prefix.appendAll(
                    // write out actual fields
                    Vector.range(0, protocols.size()).map(i -> {
                        Writer<JSONEvent, Object> w = (Writer<JSONEvent, Object>) protocols.get(i).writer();
                        return w.applyAndReset(getters.get(i).apply(value));
                    }).flatMap(Function.identity())
                );
            }
            
            @Override
            public Seq<JSONEvent> reset() {
                log.debug("{}: Resetting ", ObjectWriteProtocol.this);
                Seq<JSONEvent> prefix = (started) ? Vector.empty() : Vector.of(JSONEvent.START_OBJECT);

                started = false;
                return prefix.appendAll(
                    // write out constant-valued conditions
                    conditions.map(c -> c.writer().applyAndReset(ConstantProtocol.PRESENT)).flatMap(Function.identity())
                ).append(JSONEvent.END_OBJECT);
            }
        };
    }
    
    /**
     * Returns a new protocol that, in addition, writes out the given value when serializing.
     */
    public <U> ObjectWriteProtocol<T> having(WriteProtocol<JSONEvent, U> nestedProtocol, U value) {
        return new ObjectWriteProtocol<>(protocols, getters, conditions.append(ConstantProtocol.write(nestedProtocol, value)));
    }
    
    @Override
    public String toString() {
        String c = conditions.isEmpty() ? "" : ", " + conditions.mkString(", ");
        return "{ " + protocols.mkString(", ") + c + " }";
    }
}
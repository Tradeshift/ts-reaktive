package com.tradeshift.reaktive.json;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tradeshift.reaktive.marshal.ValidationException;

import javaslang.collection.Seq;
import javaslang.collection.Vector;
import javaslang.control.Try;

/**
 * Generic class to combine several nested FieldProtocols into reading/writing a Java object instance.  
 */
@SuppressWarnings("unchecked")
public class ObjectReadProtocol<T> extends JSONReadProtocol<T> {
    private static final Logger log = LoggerFactory.getLogger(ObjectReadProtocol.class);
    
    private final Seq<JSONReadProtocol<?>> protocols;
    private final Function<List<?>, T> produce;
    private final Seq<JSONReadProtocol<ConstantProtocol.Present>> conditions;

    public ObjectReadProtocol(
        List<JSONReadProtocol<?>> protocols,
        Function<List<?>, T> produce
    ) {
        this(Vector.ofAll(protocols), produce, Vector.empty());
    }
    
    ObjectReadProtocol(
        Seq<JSONReadProtocol<?>> protocols,
        Function<List<?>, T> produce,
        Seq<JSONReadProtocol<ConstantProtocol.Present>> conditions
    ) {
        this.protocols = protocols;
        this.produce = produce;
        this.conditions = conditions;
    }

    @Override
    public Reader<T> reader() {
        return new Reader<T>() {
            private final Seq<JSONReadProtocol<Object>> all = protocols.appendAll(conditions).map(p -> (JSONReadProtocol<Object>)p);
            private final List<Reader<Object>> readers = all.map(p -> p.reader()).toJavaList();
            private Try<Object>[] values = (Try<Object>[]) new Try<?>[readers.size()];
            private int nestedObjects = 0;
            private boolean matched = false;
            
            {
                reset();
            }
            
            @Override
            public Try<T> reset() {
                log.debug("{} resetting", this);
                Arrays.fill(values, none());
                for (int i = 0; i < protocols.size(); i++) {
                    values[i] = (Try<Object>) protocols.get(i).empty();
                }
                nestedObjects = 0;
                matched = false;
                return none();
            }
            
            @Override
            public Try<T> apply(JSONEvent evt) {
                if (nestedObjects == 0) {
                    if (evt == JSONEvent.START_OBJECT) {
                        log.debug("{} found a match", this);
                        matched = true;
                        nestedObjects++;
                        return none();
                    } else if (evt == JSONEvent.START_ARRAY) {
                        nestedObjects++;
                        return none();
                    } else { // literal, just skip
                        return none();                    
                    }
                } else if (matched && evt == JSONEvent.END_OBJECT && nestedObjects == 1) {
                    AtomicReference<Throwable> failure = new AtomicReference<>();

                    for (int i = protocols.size(); i < values.length; i++) {
                        if (isNone(values[i])) {
                            failure.set(new ValidationException("must have field " + conditions.get(i - protocols.size())));
                        }
                    }
                    
                    for (int i = 0; i < readers.size(); i++) {
                        Try<Object> r = readers.get(i).reset();
                        if (!isNone(r)) {
                            values[i] = r;                            
                        }
                    }
                    
                    Object[] args = new Object[protocols.size()];
                    for (int i = 0; i < args.length; i++) {
                        JSONReadProtocol<Object> p = (JSONReadProtocol<Object>) protocols.get(i);
                        Try<Object> t = values[i];
                        log.debug("{} said {}", p, t);
                        t.failed().forEach(failure::set);
                        args[i] = t.getOrElse((Object)null);                            
                    }
                    log.debug("Object has {}, failure is ", values, failure.get());
                    Try<T> result = (failure.get() != null) 
                        ? Try.failure(failure.get()) 
                        : Try.success(produce.apply(Arrays.asList(args))); 
                    reset();
                    return result;
                } else {
                    if (matched) {
                        for (int i = 0; i < readers.size(); i++) {
                            int idx = i;
                            Reader<Object> r = readers.get(i);
                            Try<Object> t = r.apply(evt);
                            if (t != JSONReadProtocol.NONE) {
                                values[idx] = t;
                                log.debug("   -> {}", values[idx]);
                            }
                        }
                    }
                    
                    if (evt == JSONEvent.END_ARRAY || evt == JSONEvent.END_OBJECT) {
                        nestedObjects--;
                    } else if (evt == JSONEvent.START_ARRAY || evt == JSONEvent.START_OBJECT) {
                        nestedObjects++;
                    }
                    
                    return none();
                }
            }
            
            @Override
            public String toString() {
                return ObjectReadProtocol.this.toString() + ",m=" + matched + ",n=" + nestedObjects + " ";
            }
        };
    }

    /**
     * Returns a new protocol that, in addition, also requires the given nested protocol to be present with the given constant value
     */
    public <U> ObjectReadProtocol<T> having(JSONReadProtocol<U> nestedProtocol, U value) {
        return new ObjectReadProtocol<T>(protocols, produce, conditions.append(ConstantProtocol.read(nestedProtocol, value)));
    }
    
    @Override
    public String toString() {
        return "{ " + protocols.mkString(",") + " }";
    }
}
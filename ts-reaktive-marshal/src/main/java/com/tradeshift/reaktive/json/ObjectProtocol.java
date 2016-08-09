package com.tradeshift.reaktive.json;

import java.util.List;
import java.util.function.Function;

import javaslang.Function1;
import javaslang.collection.Vector;

/**
 * Generic class to combine several nested FieldProtocols into reading/writing a Java object instance.  
 */
public class ObjectProtocol<T> extends JSONProtocol<T> {
    private final ObjectReadProtocol<T> read;
    private final ObjectWriteProtocol<T> write;

    public ObjectProtocol(
        List<JSONProtocol<?>> protocols,
        Function<List<?>, T> produce,
        List<Function1<T, ?>> getters
    ) {
        this.read = new ObjectReadProtocol<T>(Vector.ofAll(protocols), produce, Vector.empty());
        this.write = new ObjectWriteProtocol<T>(Vector.ofAll(protocols), getters, Vector.empty());
    }
    
    private ObjectProtocol(ObjectReadProtocol<T> read, ObjectWriteProtocol<T> write) {
        this.read = read;
        this.write = write;
    }

    @Override
    public Reader<T> reader() {
        return read.reader();
    }

    @Override
    public Writer<T> writer() {
        return write.writer();
    }
    
    /**
     * Returns a new protocol that, in addition, also requires the given nested protocol to be present with the given constant value,
     * writing out the value when serializing as well.
     */
    public <U> ObjectProtocol<T> having(JSONProtocol<U> nestedProtocol, U value) {
        return new ObjectProtocol<T>(read.having(nestedProtocol, value), write.having(nestedProtocol, value));
    }
    
}
package com.tradeshift.reaktive.json;

import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javaslang.control.Try;

/**
 * Protocol for reading and writing a single JSON field and its value (through an inner protocol)
 */
public class FieldProtocol {
    private static final Logger log = LoggerFactory.getLogger(FieldProtocol.class);
    
    public static <T> JSONReadProtocol<T> read(String fieldName, JSONReadProtocol<T> innerProtocol) {
        JSONEvent field = new JSONEvent.FieldName(fieldName);
        return new JSONReadProtocol<T>() {
            @Override
            protected Try<T> empty() {
                return innerProtocol.empty();
            }
            
            @Override
            public Reader<T> reader() {
                return new Reader<T>() {
                    private final Reader<T> inner = innerProtocol.reader();
                    private boolean matched;
                    private int nestedObjects = 0;
                    
                    @Override
                    public Try<T> reset() {
                        matched = false;
                        nestedObjects = 0;
                        return inner.reset();
                    }
                    
                    @Override
                    public Try<T> apply(JSONEvent evt) {
                        if (!matched && nestedObjects == 0 && evt.equals(field)) {
                            matched = true;
                            return none();
                        } else if (matched && nestedObjects == 0 && (evt == JSONEvent.START_OBJECT || evt == JSONEvent.START_ARRAY)) {
                            nestedObjects++;
                            return inner.apply(evt);
                        } else if (matched && nestedObjects == 1 && (evt == JSONEvent.END_OBJECT || evt == JSONEvent.END_ARRAY)) {
                            log.debug("Field ending.");
                            nestedObjects--;
                            matched = false;
                            return inner.apply(evt);
                        } else if (matched && nestedObjects == 0 && (evt instanceof JSONEvent.Value)) {
                            matched = false;
                            return inner.apply(evt);
                        } else {
                            Try<T> result = (matched) ? inner.apply(evt) : none();
                            if (evt == JSONEvent.START_OBJECT || evt == JSONEvent.START_ARRAY) {
                                nestedObjects++;                        
                            } else if (evt == JSONEvent.END_OBJECT || evt == JSONEvent.END_ARRAY) {
                                nestedObjects--;
                            }
                            return result;
                        }
                    }
                };
            }
            
            @Override
            public String toString() {
                return "" + field + innerProtocol;
            }            
        };
    }
    
    public static <T> JSONWriteProtocol<T> write(String fieldName, JSONWriteProtocol<T> innerProtocol) {
        JSONEvent field = new JSONEvent.FieldName(fieldName);
        
        return new JSONWriteProtocol<T>() {
            final Writer<T> writer = value -> {
                if (!innerProtocol.isEmpty(value)) {
                    return Stream.concat(Stream.of(field), innerProtocol.writer().apply(value));                
                } else {
                    return Stream.empty();
                }
            };
            
            @Override
            public boolean isEmpty(T value) {
                return innerProtocol.isEmpty(value);
            }
            
            @Override
            public Writer<T> writer() {
                return writer;
            }
            
            @Override
            public String toString() {
                return "" + field + innerProtocol;
            }
        };
    }
    
    public static <T> JSONProtocol<T> readWrite(String fieldName, JSONProtocol<T> inner) {
        JSONEvent field = new JSONEvent.FieldName(fieldName);
        JSONReadProtocol<T> read = read(fieldName, inner);
        JSONWriteProtocol<T> write = write(fieldName, inner);
        return new JSONProtocol<T>() {
            @Override
            protected Try<T> empty() {
                return read.empty();
            }
            
            @Override
            public boolean isEmpty(T value) {
                return write.isEmpty(value);
            }

            @Override
            public Writer<T> writer() {
                return write.writer();
            }

            @Override
            public Reader<T> reader() {
                return read.reader();
            }
            
            @Override
            public String toString() {
                return "" + field + inner;
            }            
        };
    }
}

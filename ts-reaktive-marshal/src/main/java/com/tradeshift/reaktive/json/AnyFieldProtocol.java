package com.tradeshift.reaktive.json;

import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javaslang.Tuple;
import javaslang.Tuple2;
import javaslang.control.Option;
import javaslang.control.Try;

/**
 * Protocol for reading and writing a single JSON field and its value (through an inner protocol), matching all fields
 */
public class AnyFieldProtocol {
    private static final Logger log = LoggerFactory.getLogger(AnyFieldProtocol.class);
    
    public static <T> JSONReadProtocol<Tuple2<String,T>> read(JSONReadProtocol<T> innerProtocol) {
        
        return new JSONReadProtocol<Tuple2<String,T>>() {
            @Override
            public Reader<Tuple2<String, T>> reader() {
                return new Reader<Tuple2<String,T>>() {
                    private final Reader<T> inner = innerProtocol.reader();
                    private boolean matched;
                    private int nestedObjects = 0;
                    private Option<String> lastField = Option.none();
                    
                    @Override
                    public Try<Tuple2<String,T>> reset() {
                        matched = false;
                        nestedObjects = 0;
                        Try<T> i = inner.reset();
                        Try<Tuple2<String,T>> result = tuple(i);
                        lastField = Option.none();
                        return result;
                    }

                    private Try<Tuple2<String, T>> tuple(Try<T> i) {
                        return lastField.isDefined() ? i.map(t -> Tuple.of(lastField.get(), t)) : none();
                    }
                    
                    @Override
                    public Try<Tuple2<String,T>> apply(JSONEvent evt) {
                        if (!matched && nestedObjects == 0 && evt instanceof JSONEvent.FieldName) {
                            matched = true;
                            lastField = Option.some(JSONEvent.FieldName.class.cast(evt).getName());
                            log.debug("AnyField started: {}", lastField);
                            return none();
                        } else if (matched && nestedObjects == 0 && (evt == JSONEvent.START_OBJECT || evt == JSONEvent.START_ARRAY)) {
                            nestedObjects++;
                            return tuple(inner.apply(evt));
                        } else if (matched && nestedObjects == 1 && (evt == JSONEvent.END_OBJECT || evt == JSONEvent.END_ARRAY)) {
                            log.debug("AnyField ending nested.");
                            nestedObjects--;
                            matched = false;
                            return tuple(inner.apply(evt));
                        } else if (matched && nestedObjects == 0 && (evt instanceof JSONEvent.Value)) {
                            log.debug("AnyField ending value.");
                            matched = false;
                            return tuple(inner.apply(evt));
                        } else {
                            Try<T> result = (matched) ? inner.apply(evt) : none();
                            if (evt == JSONEvent.START_OBJECT || evt == JSONEvent.START_ARRAY) {
                                nestedObjects++;                        
                            } else if (evt == JSONEvent.END_OBJECT || evt == JSONEvent.END_ARRAY) {
                                nestedObjects--;
                            }
                            return tuple(result);
                        }
                    }
                };
            }
            
            @Override
            public String toString() {
                return "(any): " + innerProtocol;
            }            
        };
    }
    
    public static <T> JSONWriteProtocol<Tuple2<String,T>> write(JSONWriteProtocol<T> innerProtocol) {
        return new JSONWriteProtocol<Tuple2<String,T>>() {
            final Writer<Tuple2<String,T>> writer = value -> {
                if (!innerProtocol.isEmpty(value._2)) {
                    return Stream.concat(Stream.of(new JSONEvent.FieldName(value._1)), innerProtocol.writer().apply(value._2));
                } else {
                    return Stream.empty();
                }
            };
            
            @Override
            public Writer<Tuple2<String,T>> writer() {
                return writer;
            }
            
            @Override
            public String toString() {
                return "(any): " + innerProtocol;
            }
        };
    }
    
    public static <T> JSONProtocol<Tuple2<String,T>> readWrite(JSONProtocol<T> inner) {
        JSONReadProtocol<Tuple2<String,T>> read = read(inner);
        JSONWriteProtocol<Tuple2<String,T>> write = write(inner);
        return new JSONProtocol<Tuple2<String,T>>() {
            @Override
            public Writer<Tuple2<String,T>> writer() {
                return write.writer();
            }

            @Override
            public Reader<Tuple2<String,T>> reader() {
                return read.reader();
            }
            
            @Override
            public String toString() {
                return "(any): " + inner;
            }
        };
    }
}

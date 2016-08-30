package com.tradeshift.reaktive.json;

import static com.tradeshift.reaktive.marshal.ReadProtocol.none;

import java.util.Iterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tradeshift.reaktive.marshal.ReadProtocol;
import com.tradeshift.reaktive.marshal.Reader;
import com.tradeshift.reaktive.marshal.WriteProtocol;
import com.tradeshift.reaktive.marshal.Writer;

import javaslang.control.Try;

/**
 * Protocol for reading and writing a single JSON field and its value (through an inner protocol)
 */
public class FieldProtocol {
    private static final Logger log = LoggerFactory.getLogger(FieldProtocol.class);
    
    public static <T> ReadProtocol<JSONEvent, T> read(String fieldName, ReadProtocol<JSONEvent, T> innerProtocol) {
        JSONEvent field = new JSONEvent.FieldName(fieldName);
        return new ReadProtocol<JSONEvent, T>() {
            @Override
            public Try<T> empty() {
                return innerProtocol.empty();
            }
            
            @Override
            public Reader<JSONEvent, T> reader() {
                return new Reader<JSONEvent, T>() {
                    private final Reader<JSONEvent, T> inner = innerProtocol.reader();
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
    
    public static <T> WriteProtocol<JSONEvent, T> write(String fieldName, WriteProtocol<JSONEvent, T> innerProtocol) {
        JSONEvent field = new JSONEvent.FieldName(fieldName);
        
        return new WriteProtocol<JSONEvent, T>() {
            final Writer<JSONEvent, T> writer = value -> {
                return concatIfSecondNotEmpty(Stream.of(field), innerProtocol.writer().apply(value));
            };
            
            @Override
            public Class<? extends JSONEvent> getEventType() {
                return JSONEvent.class;
            }
            
            @Override
            public Writer<JSONEvent, T> writer() {
                return writer;
            }
            
            @Override
            public String toString() {
                return "" + field + innerProtocol;
            }
        };
    }
    
    /**
     * Returns a stream consisting of [first] and then [second], unless second is empty (or an empty JSON array);
     * then an empty stream is returned.
     */
    static Stream<JSONEvent> concatIfSecondNotEmpty(Stream<JSONEvent> first, Stream<JSONEvent> second) {
        Iterator<JSONEvent> iterator = second.iterator();
        if (iterator.hasNext()) {
            JSONEvent ev1 = iterator.next();
            if (iterator.hasNext()) {
                JSONEvent ev2 = iterator.next();
                if (iterator.hasNext()) {
                    return Stream.concat(first, Stream.concat(Stream.of(ev1, ev2), StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, 0), false)));
                } else if (ev1 == JSONEvent.START_ARRAY && ev2 == JSONEvent.END_ARRAY) {
                    return Stream.empty();
                } else {
                    return Stream.concat(first, Stream.of(ev1, ev2));
                }
            } else {
                return Stream.concat(first, Stream.of(ev1));
            }
        } else {
            return Stream.empty();
        }
    }
}

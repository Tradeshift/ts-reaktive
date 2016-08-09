package com.tradeshift.reaktive.json;

import java.util.function.Function;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javaslang.Function1;
import javaslang.collection.Seq;
import javaslang.control.Try;

public class SeqProtocol {
    private static final Logger log = LoggerFactory.getLogger(SeqProtocol.class);
    
    /**
     * @param factory Function that creates a singleton collection around a value
     * @param empty Empty collection
     */
    public static <T, S extends Seq<T>> JSONReadProtocol<S> read(JSONReadProtocol<T> inner, Function1<T, S> factory, S empty) {
        final Try<S> EMPTY = Try.success(empty);
        
        return new JSONReadProtocol<S>() {
            @Override
            public Reader<S> reader() {
                return new Reader<S>() {
                    final Reader<T> innerReader = inner.reader();
                    Try<S> items = none();

                    @Override
                    public Try<S> apply(JSONEvent evt) {
                        Try<T> i = innerReader.apply(evt);
                        add(i);
                        return none();
                    }

                    @Override
                    public Try<S> reset() {
                        add(innerReader.reset());
                        Try<S> result = items;
                        items = none();
                        log.debug("  reset(): {},", result);
                        return result;
                    }
                    
                    @SuppressWarnings("unchecked")
                    private void add(Try<T> i) {
                        if (!isNone(i)) {
                            items = (isNone(items) ? empty() : items).flatMap(seq -> i.map(t -> (S) seq.append(t)));
                        }                        
                    }
                };
                
            }
            
            @Override
            protected Try<S> empty() {
                return EMPTY;
            }

            @Override
            public String toString() {
                return "seq(" + inner + ")";
            }            
        };
    }
    
    public static <T> JSONWriteProtocol<Seq<T>> write(JSONWriteProtocol<T> inner) {
        return new JSONWriteProtocol<Seq<T>>() {
            @Override
            public Writer<Seq<T>> writer() {
                Writer<T> parentWriter = inner.writer();
                return value -> {
                    Seq<Stream<JSONEvent>> streams = value.map(parentWriter::apply);
                    return streams.toJavaStream().flatMap(Function.identity());                
                };
            }
            
            @Override
            public boolean isEmpty(Seq<T> value) {
                return value.isEmpty();
            }
            
            @Override
            public String toString() {
                return "seq(" + inner + ")";
            }                        
        };
    }
    
    /**
     * @param factory Function that creates a singleton collection around a value
     * @param empty Empty collection
     */
    public static <T, S extends Seq<T>> JSONProtocol<S> readWrite(JSONProtocol<T> inner, Function1<T, S> factory, S empty) {
        JSONReadProtocol<S> readV = read(inner, factory, empty);
        JSONWriteProtocol<Seq<T>> writeV = write(inner);
        return new JSONProtocol<S>() {
            @Override
            public Writer<S> writer() {
                return Writer.widen(writeV.writer());
            }

            @Override
            public Reader<S> reader() {
                return readV.reader();
            }
            
            @Override
            protected Try<S> empty() {
                return readV.empty();
            }
            
            @Override
            public boolean isEmpty(S value) {
                return writeV.isEmpty(value);
            }
            
            @Override
            public String toString() {
                return "seq(" + inner + ")";
            }                        
        };
    }    
}

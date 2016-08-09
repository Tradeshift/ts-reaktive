package com.tradeshift.reaktive.json;

import java.util.function.Function;
import java.util.stream.Stream;

import javaslang.Function1;
import javaslang.Tuple2;
import javaslang.collection.Map;
import javaslang.collection.Seq;
import javaslang.control.Try;

public class MapProtocol {
    /**
     * @param factory Function that creates a singleton collection around a value
     * @param empty Empty collection
     */
    public static <K,V,M extends Map<K,V>> JSONReadProtocol<M> read(JSONReadProtocol<Tuple2<K,V>> inner, Function1<Tuple2<K,V>,M> factory, M empty) {
        final Try<M> EMPTY = Try.success(empty);
        
        return new JSONReadProtocol<M>() {
            @Override
            public JSONReadProtocol.Reader<M> reader() {
                return new Reader<M>() {
                    final Reader<Tuple2<K,V>> innerReader = inner.reader();
                    Try<M> items = none();

                    @Override
                    public Try<M> apply(JSONEvent evt) {
                        Try<Tuple2<K,V>> i = innerReader.apply(evt);
                        add(i);
                        return none();
                    }

                    @Override
                    public Try<M> reset() {
                        add(innerReader.reset());
                        Try<M> result = items;
                        items = none();
                        return result;
                    }
                    
                    @SuppressWarnings("unchecked")
                    private void add(Try<Tuple2<K,V>> i) {
                        if (!isNone(i)) {
                            items = (isNone(items) ? empty() : items).flatMap(m -> i.map(t -> (M) m.put(t)));
                        }                        
                    }
                };
            }
            
            @Override
            protected Try<M> empty() {
                return EMPTY;
            }
            
            @Override
            public String toString() {
                return "map(" + inner + ")";
            }
        };
    }

    public static <K,V> JSONWriteProtocol<Map<K,V>> write(JSONWriteProtocol<Tuple2<K,V>> inner) {
        return new JSONWriteProtocol<Map<K,V>>() {
            @Override
            public Writer<Map<K,V>> writer() {
                Writer<Tuple2<K,V>> parentWriter = inner.writer();
                return value -> {
                    Seq<Stream<JSONEvent>> streams = value.map(parentWriter::apply);
                    return streams.toJavaStream().flatMap(Function.identity());                
                };
            }
            
            @Override
            public boolean isEmpty(Map<K,V> value) {
                return value.isEmpty();
            }
            
            @Override
            public String toString() {
                return "map(" + inner + ")";
            }                        
        };
    }
    
    /**
     * @param factory Function that creates a singleton collection around a value
     * @param empty Empty collection
     */
    public static <K,V,M extends Map<K,V>> JSONProtocol<M> readWrite(JSONProtocol<Tuple2<K,V>> inner, Function1<Tuple2<K,V>,M> factory, M empty) {
        JSONReadProtocol<M> readV = read(inner, factory, empty);
        JSONWriteProtocol<Map<K,V>> writeV = write(inner);
        return new JSONProtocol<M>() {
            @Override
            public Writer<M> writer() {
                return Writer.widen(writeV.writer());
            }

            @Override
            public Reader<M> reader() {
                return readV.reader();
            }
            
            @Override
            protected Try<M> empty() {
                return readV.empty();
            }
            
            @Override
            public boolean isEmpty(M value) {
                return writeV.isEmpty(value);
            }
            
            @Override
            public String toString() {
                return "map(" + inner + ")";
            }            
        };
    }
}

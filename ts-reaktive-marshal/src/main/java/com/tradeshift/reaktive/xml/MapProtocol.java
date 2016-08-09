package com.tradeshift.reaktive.xml;

import java.util.function.Function;
import java.util.stream.Stream;

import javax.xml.stream.events.XMLEvent;

import javaslang.Function1;
import javaslang.Tuple2;
import javaslang.collection.Map;
import javaslang.collection.Seq;
import javaslang.control.Try;

/**
 * Reads and writes an inner read/write protocol of tuples, which may match multiple times, 
 * by mapping it to an immutable {@link javaslang.collection.Map}.
 */
public class MapProtocol {
    /**
     * @param factory Function that creates a singleton collection around a value
     * @param empty Empty collection
     */
    public static <K,V,M extends Map<K,V>> XMLReadProtocol<M> read(XMLReadProtocol<Tuple2<K,V>> inner, Function1<Tuple2<K,V>,M> factory, M empty) {
        final Try<M> EMPTY = Try.success(empty);
        
        return new XMLReadProtocol<M>() {
            @Override
            public Reader<M> reader() {
                return inner.reader().map(factory);
            }
            
            @Override
            protected Try<M> empty() {
                return EMPTY;
            }
            
            @SuppressWarnings("unchecked")
            @Override
            protected Try<M> combine(Try<M> previous, Try<M> current) {
                if (previous.isSuccess() && current.isSuccess()) {
                    return (Try<M>) Try.success(current.get().merge(previous.get())); // current should take preference over previous
                } else if (previous.isFailure() || isNone(current)) {
                    return previous;
                } else  {
                    return current;
                }
            }
            
            @Override
            public String toString() {
                return "map(" + inner + ")";
            }
        };
    }
    
    public static <K,V> XMLWriteProtocol<Map<K,V>> write(XMLWriteProtocol<Tuple2<K,V>> inner) {
        return new XMLWriteProtocol<Map<K,V>>() {
            @Override
            public Writer<Map<K, V>> writer() {
                Writer<Tuple2<K, V>> parentWriter = inner.writer();
                return value -> {
                    Seq<Stream<XMLEvent>> streams = value.map(parentWriter::apply);
                    return streams.toJavaStream().flatMap(Function.identity());                
                };
            }

            @Override
            public boolean isAttributeProtocol() {
                return inner.isAttributeProtocol();
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
    public static <K,V,M extends Map<K,V>> XMLProtocol<M> readWrite(XMLProtocol<Tuple2<K,V>> inner, Function1<Tuple2<K,V>,M> factory, M empty) {
        XMLReadProtocol<M> readV = read(inner, factory, empty);
        XMLWriteProtocol<Map<K,V>> writeV = write(inner);
        return new XMLProtocol<M>() {
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
            protected Try<M> combine(Try<M> previous, Try<M> current) {
                return readV.combine(previous, current);
            }
            
            @Override
            public boolean isAttributeProtocol() {
                return writeV.isAttributeProtocol();
            }
            
            @Override
            public String toString() {
                return "seq(" + inner + ")";
            }            
        };
    }    
}

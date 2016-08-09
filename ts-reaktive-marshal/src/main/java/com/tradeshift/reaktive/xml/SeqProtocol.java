package com.tradeshift.reaktive.xml;

import java.util.function.Function;
import java.util.stream.Stream;

import javax.xml.stream.events.XMLEvent;

import javaslang.Function1;
import javaslang.collection.Seq;
import javaslang.control.Try;

/**
 * Reads and writes an inner read/write protocol that may match multiple times, 
 * by mapping it to an immutable {@link javaslang.collection.Seq}.
 */
public class SeqProtocol {
    /**
     * @param factory Function that creates a singleton collection around a value
     * @param empty Empty collection
     */
    public static <T, S extends Seq<T>> XMLReadProtocol<S> read(XMLReadProtocol<T> inner, Function1<T, S> factory, S empty) {
        final Try<S> EMPTY = Try.success(empty);
        
        return new XMLReadProtocol<S>() {
            @Override
            public Reader<S> reader() {
                return inner.reader().map(factory);
            }
            
            @Override
            protected Try<S> empty() {
                return EMPTY;
            }
            
            @SuppressWarnings("unchecked")
            @Override
            protected Try<S> combine(Try<S> previous, Try<S> current) {
                if (previous.isSuccess() && current.isSuccess()) {
                    return (Try<S>) Try.success(previous.get().appendAll(current.get()));
                } else if (previous.isFailure() || isNone(current)) {
                    return previous;
                } else  {
                    return current;
                }
            }
            
            @Override
            public String toString() {
                return "seq(" + inner + ")";
            }            
        };
    }
    
    public static <T> XMLWriteProtocol<Seq<T>> write(XMLWriteProtocol<T> inner) {
        return new XMLWriteProtocol<Seq<T>>() {
            @Override
            public Writer<Seq<T>> writer() {
                Writer<T> parentWriter = inner.writer();
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
                return "seq(" + inner + ")";
            }            
        };
    }
    
    /**
     * @param factory Function that creates a singleton collection around a value
     * @param empty Empty collection
     */
    public static <T, S extends Seq<T>> XMLProtocol<S> readWrite(XMLProtocol<T> inner, Function1<T, S> factory, S empty) {
        XMLReadProtocol<S> readV = read(inner, factory, empty);
        XMLWriteProtocol<Seq<T>> writeV = write(inner);
        return new XMLProtocol<S>() {
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
            protected Try<S> combine(Try<S> previous, Try<S> current) {
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

package com.tradeshift.reaktive.xml;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.StreamSupport;

import javaslang.Function1;
import javaslang.control.Try;

/**
 * Write support for generic Iterable, and read support for mutable collection classes.
 */
public class IterableProtocol {
    /**
     * @param factory Function that creates a new mutable collection with a single value
     * @param add Consumer that adds an element to the collection
     * @param empty Empty collection instance (global, it won't be modified) 
     */
    public static <T, C extends Iterable<T>> XMLReadProtocol<C> read(XMLReadProtocol<T> inner, C empty, Function1<T,C> factory, BiConsumer<C,T> add) {
        final Try<C> EMPTY = Try.success(empty);
        
        return new XMLReadProtocol<C>() {
            @Override
            public Reader<C> reader() {
                return inner.reader().map(factory);
            }
            
            @Override
            protected Try<C> empty() {
                return EMPTY;
            }
            
            @Override
            protected Try<C> combine(Try<C> previous, Try<C> current) {
                if (previous.isSuccess() && current.isSuccess()) {
                    add.accept(previous.get(), current.get().iterator().next());
                    return previous;
                } else if (previous.isFailure() || isNone(current)) {
                    return previous;
                } else  {
                    return current;
                }
            }
            
            @Override
            public String toString() {
                return "iterable(" + inner + ")";
            }            
        };
    }
    
    public static <T> XMLWriteProtocol<Iterable<T>> write(XMLWriteProtocol<T> inner) {
        return new XMLWriteProtocol<Iterable<T>>() {
            @Override
            public Writer<Iterable<T>> writer() {
                Writer<T> parentWriter = inner.writer();
                return value -> {
                    return StreamSupport.stream(value.spliterator(), false)
                        .map(parentWriter::apply)
                        .flatMap(Function.identity());                
                };
            }
            
            @Override
            public boolean isAttributeProtocol() {
                return inner.isAttributeProtocol();
            }
            
            @Override
            public String toString() {
                return "iterable(" + inner + ")";
            }            
        };
    }
    
    /**
     * @param factory Function that creates a new mutable collection with a single value
     * @param add Consumer that adds an element to the collection
     * @param empty Empty collection instance (global, it won't be modified) 
     */
    public static <T, C extends Iterable<T>> XMLProtocol<C> readWrite(XMLProtocol<T> inner, C empty, Function1<T,C> factory, BiConsumer<C,T> add) {
        XMLReadProtocol<C> readV = read(inner, empty, factory, add);
        XMLWriteProtocol<Iterable<T>> writeV = write(inner);
        return new XMLProtocol<C>() {
            @Override
            public Writer<C> writer() {
                return Writer.widen(writeV.writer());
            }

            @Override
            public Reader<C> reader() {
                return readV.reader();
            }
            
            @Override
            protected Try<C> empty() {
                return readV.empty();
            }
            
            @Override
            protected Try<C> combine(Try<C> previous, Try<C> current) {
                return readV.combine(previous, current);
            }
            
            @Override
            public boolean isAttributeProtocol() {
                return writeV.isAttributeProtocol();
            }
            
            @Override
            public String toString() {
                return "iterable(" + inner + ")";
            }            
        };
    }
    
}

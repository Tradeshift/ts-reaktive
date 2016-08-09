package com.tradeshift.reaktive.json;

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
    public static <T, C extends Iterable<T>> JSONReadProtocol<C> read(JSONReadProtocol<T> inner, C empty, Function1<T,C> factory, BiConsumer<C,T> add) {
        final Try<C> EMPTY = Try.success(empty);
        
        return new JSONReadProtocol<C>() {
            @Override
            public Reader<C> reader() {
                return new Reader<C>() {
                    final Reader<T> innerReader = inner.reader();
                    Try<C> items = none();

                    @Override
                    public Try<C> apply(JSONEvent evt) {
                        Try<T> i = innerReader.apply(evt);
                        add(i);
                        return none();
                    }

                    @Override
                    public Try<C> reset() {
                        add(innerReader.reset());
                        Try<C> result = items;
                        items = none();
                        return result;
                    }
                    
                    private void add(Try<T> i) {
                        if (!isNone(i)) {
                            if (isNone(items)) {
                                items = i.map(factory);
                            } else {
                                items = items.flatMap(c -> i.map(t -> {
                                    add.accept(c, t);
                                    return c;
                                }));
                            }
                        }                        
                    }
                };
                
            }
            
            @Override
            protected Try<C> empty() {
                return EMPTY;
            }

            @Override
            public String toString() {
                return "iterable(" + inner + ")";
            }            
        };        
    }
    
    public static <T> JSONWriteProtocol<Iterable<T>> write(JSONWriteProtocol<T> inner) {
        return new JSONWriteProtocol<Iterable<T>>() {
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
            public boolean isEmpty(Iterable<T> value) {
                return value.iterator().hasNext();
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
    public static <T, C extends Iterable<T>> JSONProtocol<C> readWrite(JSONProtocol<T> inner, C empty, Function1<T,C> factory, BiConsumer<C,T> add) {
        JSONReadProtocol<C> readV = read(inner, empty, factory, add);
        JSONWriteProtocol<Iterable<T>> writeV = write(inner);
        return new JSONProtocol<C>() {
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
            public boolean isEmpty(C value) {
                return writeV.isEmpty(value);
            }
            
            @Override
            public String toString() {
                return "iterable(" + inner + ")";
            }                        
        };        
    }
}

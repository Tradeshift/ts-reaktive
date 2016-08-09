package com.tradeshift.reaktive;

import java.util.Iterator;
import java.util.Spliterators;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javaslang.control.Option;

/**
 * Various methods dealing with Java 8 streams
 */
public class JavaStreams {
    
    /**
     * Has the given aggregation function process all elements of the stream, starting with the given initial element.
     */
    @SuppressWarnings("unchecked")
    public static <T,U> U foldLeft(U initial, Stream<T> stream, BiFunction<U,T,U> f) {
        U u = initial;
        for (Object t: stream.toArray()) {
            u = f.apply(u, (T) t);
        }
        return u;
    }
    
    /**
     * Returns Option.none() if the stream is empty, or an Option.of(stream) if there are elements in the stream.
     */
    public static <T> Option<Stream<T>> toOption(Stream<T> stream) {
        Iterator<T> iterator = stream.iterator();
        if (iterator.hasNext()) {
            return Option.of(StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, 0), false));
        } else {
            return Option.none();
        }
    }    
}

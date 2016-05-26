package com.tradeshift.reaktive;

import java.util.function.BiFunction;
import java.util.stream.Stream;

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
}

package com.tradeshift.reaktive.json;

import java.util.stream.Stream;

import javaslang.Function1;

public interface JSONWriteProtocol<T> {
    public Writer<T> writer();

    public interface Writer<T> {
        Stream<JSONEvent> apply(T value);
        
        public default <U> Writer<U> compose(Function1<U,T> f) {
            Writer<T> parent = this;
            return new Writer<U>() {
                @Override
                public Stream<JSONEvent> apply(U value) {
                    return parent.apply(f.apply(value));
                }
            };
        }

        /**
         * Widens a writer of T to become a writer of a subclass of T. This is OK, since a writer of T
         * can also write all subclasses of T.
         */
        @SuppressWarnings("unchecked")
        static <T, U extends T> Writer<U> widen(Writer<T> w) {
            return (Writer<U>) w;
        }
    }
    
    public default boolean isEmpty(T value) {
        return false;
    }
    
    /**
     * Maps the protocol into a different type, invoking [beforeWrite] before writing.
     */
    public default <U> JSONWriteProtocol<U> compose(Function1<U,T> beforeWrite) {
        JSONWriteProtocol<T> parent = this;
        return new JSONWriteProtocol<U>() {
            @Override
            public Writer<U> writer() {
                return parent.writer().compose(beforeWrite);
            }
            
            @Override
            public boolean isEmpty(U value) {
                return parent.isEmpty(beforeWrite.apply(value));
            }
        };
    };
    
}

package com.tradeshift.reaktive.xml;

import java.util.stream.Stream;

import javax.xml.stream.events.XMLEvent;

import javaslang.Function1;

public interface XMLWriteProtocol<T> {
    public Writer<T> writer();
    
    /**
     * Whether this protocol only writes Attribute events
     */
    public boolean isAttributeProtocol();

    public interface Writer<T> {
        Stream<XMLEvent> apply(T value);
        
        public default <U> Writer<U> compose(Function1<U,T> f) {
            Writer<T> parent = this;
            return new Writer<U>() {
                @Override
                public Stream<XMLEvent> apply(U value) {
                    return parent.apply(f.apply(value));
                }
            };
        }
        
        /**
         * Widens a writer of T to become a writer of a subclass of T. This is OK, since a writer of T
         * can also write all subclasses of T.
         */
        @SuppressWarnings("unchecked")
        public static <T, U extends T> Writer<U> widen(Writer<T> w) {
            return (Writer<U>) w;
        }
    }
}

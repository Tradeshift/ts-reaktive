package com.tradeshift.reaktive.xml;

import com.tradeshift.reaktive.marshal.StringMarshallable;

import javaslang.Tuple;
import javaslang.Tuple2;

public abstract class TStringProtocol<T1> extends XMLProtocol<Tuple2<T1, String>> {
    /**
     * Converts _2 of the tuple to a different type.
     */
    public <T2> XMLProtocol<Tuple2<T1,T2>> as(StringMarshallable<T2> type) {
        return new XMLProtocol<Tuple2<T1,T2>>() {

            @Override
            public Writer<Tuple2<T1, T2>> writer() {
                return TStringProtocol.this.writer().compose(t -> t.map2(type::write));
            }

            @Override
            public boolean isAttributeProtocol() {
                return TStringProtocol.this.isAttributeProtocol();
            }

            @Override
            public Reader<Tuple2<T1, T2>> reader() {
                return TStringProtocol.this.reader().flatMap(tuple -> type.tryRead(tuple._2()).map(t -> Tuple.of(tuple._1(), t)));
            }
        };
    }    
    
}

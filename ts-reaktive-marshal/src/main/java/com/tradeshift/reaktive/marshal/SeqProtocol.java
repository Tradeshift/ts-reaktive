package com.tradeshift.reaktive.marshal;

import com.tradeshift.reaktive.marshal.IterableProtocol.IterableReadProtocol;
import com.tradeshift.reaktive.marshal.IterableProtocol.IterableWriteProtocol;

import io.vavr.collection.Seq; 

public class SeqProtocol {
    @SuppressWarnings("unchecked")
    public static <E,T,S extends Seq<T>> IterableReadProtocol<E, S> read(ReadProtocol<E, T> inner, S empty) {
        return FoldProtocol.read("*", inner, () -> empty, (seq, t) -> (S) seq.append(t));
    }

    public static <E,T> IterableWriteProtocol<E,Seq<T>> write(WriteProtocol<E,T> inner) {
        return IterableProtocol.write(inner);
    }
}

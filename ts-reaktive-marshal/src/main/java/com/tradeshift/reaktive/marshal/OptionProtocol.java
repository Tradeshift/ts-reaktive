package com.tradeshift.reaktive.marshal;

import io.vavr.control.Option;
import com.tradeshift.reaktive.marshal.IterableProtocol.IterableReadProtocol;
import com.tradeshift.reaktive.marshal.IterableProtocol.IterableWriteProtocol;

public class OptionProtocol {
    
    public static <E,T> IterableReadProtocol<E,Option<T>> read(ReadProtocol<E,T> inner) {
        return FoldProtocol.read("option", inner, () -> Option.<T>none(), (opt, t) -> Option.some(t));
    }

    public static <E,T> IterableWriteProtocol<E,Option<T>> write(WriteProtocol<E,T> inner) {
        return IterableProtocol.write(inner);
    }
}

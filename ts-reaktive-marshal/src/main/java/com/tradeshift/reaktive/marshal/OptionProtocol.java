package com.tradeshift.reaktive.marshal;

import javaslang.control.Option;

public class OptionProtocol {
    
    public static <E,T> ReadProtocol<E,Option<T>> read(ReadProtocol<E,T> inner) {
        return FoldProtocol.read("option", inner, () -> Option.<T>none(), (opt, t) -> Option.some(t));
    }

    public static <E,T> WriteProtocol<E,Option<T>> write(WriteProtocol<E,T> inner) {
        return WriteProtocol.narrow(IterableProtocol.write(inner));
    }
}

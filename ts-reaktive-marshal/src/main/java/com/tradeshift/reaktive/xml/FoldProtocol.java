package com.tradeshift.reaktive.xml;

import javaslang.Function1;
import javaslang.Function2;
import javaslang.control.Try;

/**
 * Folds over a repeated nested protocol, merging the results into a single element. Only for read protocols.
 */
public class FoldProtocol {
    // FIXME put a proper foldLeft signature here, after changing Reader.reset() from void to Try<T>, and removing XMLReadProtocol.combine().
    public static <T,U> XMLReadProtocol<U> read(XMLReadProtocol<T> parent, Function1<T,U> map, U initial, Function2<U,U,U> combine) {
        return new XMLReadProtocol<U>() {

            @Override
            public Reader<U> reader() {
                return parent.reader().map(map);
            }
            
            @Override
            protected Try<U> empty() {
                return Try.success(initial);
            }
            
            @Override
            protected Try<U> combine(Try<U> previous, Try<U> current) {
                if (previous.isSuccess() && current.isSuccess()) {
                    return Try.success(combine.apply(previous.get(), current.get()));
                } else if (previous.isFailure() || isNone(current)) {
                    return previous;
                } else  {
                    return current;
                }
            }
        };
    }
}

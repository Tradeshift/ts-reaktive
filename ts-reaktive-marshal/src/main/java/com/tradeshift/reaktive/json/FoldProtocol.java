package com.tradeshift.reaktive.json;

import java.util.function.Supplier;

import javaslang.Function2;
import javaslang.control.Option;
import javaslang.control.Try;

/**
 * Folds over a repeated nested protocol, merging the results into a single element. Only for read protocols.
 */
public class FoldProtocol {
    public static <T,U> JSONReadProtocol<U> read(JSONReadProtocol<T> parent, Supplier<U> initial, Function2<U,T,U> combine) {
        return new JSONReadProtocol<U>() {
            @Override
            public Reader<U> reader() {
                Reader<T> parentReader = parent.reader();
                return new Reader<U>() {
                    Option<U> value = Option.none();
                    
                    @Override
                    public Try<U> apply(JSONEvent evt) {
                        Try<T> result = parentReader.apply(evt);
                        if (result.isSuccess()) {
                            value = Option.some(combine.apply(value.getOrElse(initial), result.get()));
                            return empty();
                        } else if (isNone(result)) {
                            // skip over
                            return empty();
                        } else {
                            // failure: emit immediately, throw away rest
                            value = Option.none();
                            return result.map(t->null);
                        }
                    }

                    @Override
                    public Try<U> reset() {
                        Try<U> result = (value.isDefined()) ? value.toTry() : Try.success(initial.get());
                        value = Option.none();
                        return result;
                    }
                };
            }
        };
    }

}

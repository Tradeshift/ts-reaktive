package com.tradeshift.reaktive.marshal;

import static com.tradeshift.reaktive.marshal.ReadProtocol.isNone;
import static com.tradeshift.reaktive.marshal.ReadProtocol.none;

import java.util.function.Function;

import io.vavr.collection.Seq;
import io.vavr.collection.Vector;
import io.vavr.control.Try;

/**
 * Forwards read events to multiple alternative protocols, emitting whenever any of the alternatives emit.
 * If multiple alternatives emit for the same event, all results are emitted.
 * If at least one alternative emits for an event, any errors on other alternatives are ignored.
 * If all alternatives yield errors for an event, the errors are concatenated and escalated.
 */
public class CombinedProtocol<E,T> implements ReadProtocol<E,Seq<T>> {
    private final Seq<ReadProtocol<E,T>> alternatives;

    public CombinedProtocol(Seq<ReadProtocol<E,T>> alternatives) {
        this.alternatives = alternatives;
    }

    @Override
    public Reader<E,Seq<T>> reader() {
        Seq<Reader<E,T>> readers = alternatives.map(p -> p.reader());
        return new Reader<E,Seq<T>>() {
            @Override
            public Try<Seq<T>> reset() {
                return perform(r -> r.reset());
            }

            @Override
            public Try<Seq<T>> apply(E evt) {
                return perform(r -> r.apply(evt));
            }

            private Try<Seq<T>> perform(Function<Reader<E,T>, Try<T>> f) {
                Try<Seq<T>> result = none();
                for (Reader<E,T> reader: readers) {
                    Try<T> readerResult = f.apply(reader);
                    if (!isNone(readerResult)) {
                        if (isNone(result) || (result.isFailure() && readerResult.isSuccess())) {
                            result = readerResult.map(Vector::of);
                        } else if (!result.isFailure() && readerResult.isSuccess()) {
                            result = result.map(seq -> seq.append(readerResult.get()));
                        } else if (readerResult.isFailure() && result.isFailure()) {
                            result = Try.failure(new IllegalArgumentException(result.failed().get().getMessage() + ", alternatively " + readerResult.failed().get().getMessage()));
                        }
                    }
                }
                return result;
            }
        };
    }
}

package com.tradeshift.reaktive.json;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javaslang.collection.Seq;
import javaslang.control.Try;

/**
 * Forwards read events to multiple alternative protocols, emitting whenever any of the alternatives emit. If multiple 
 * alternatives emit for the same event, the first one wins.
 * 
 * TODO how to do writing
 */
public class AlternativesProtocol<T> extends JSONReadProtocol<T> {
    private static final Logger log = LoggerFactory.getLogger(AlternativesProtocol.class); 
    
    private final Seq<JSONReadProtocol<T>> alternatives;

    public AlternativesProtocol(Seq<JSONReadProtocol<T>> alternatives) {
        this.alternatives = alternatives;
    }

    @Override
    public Reader<T> reader() {
        Seq<Reader<T>> readers = alternatives.map(p -> p.reader());
        return new Reader<T>() {
            @Override
            public Try<T> reset() {
                Try<T> result = none();
                for (Reader<T> reader: readers) {
                    Try<T> readerResult = reader.reset();
                    log.debug("reset: reader {} said {}", reader, readerResult);
                    if (!isNone(readerResult)) {
                        if (isNone(result) || (result.isFailure() && readerResult.isSuccess())) {
                            result = readerResult;
                        } else if (readerResult.isFailure() && result.isFailure()) {
                            result = Try.failure(new IllegalArgumentException(result.failed().get().getMessage() + ", alternatively " + readerResult.failed().get().getMessage()));
                        }
                    }
                }
                return result;
            }

            @Override
            public Try<T> apply(JSONEvent evt) {
                Try<T> result = none();
                for (Reader<T> reader: readers) {
                    Try<T> readerResult = reader.apply(evt);
                    log.debug("apply: reader {} said {}", reader, readerResult);
                    if (!isNone(readerResult)) {
                        if (isNone(result) || (result.isFailure() && readerResult.isSuccess())) {
                            result = readerResult;
                        } else if (readerResult.isFailure() && result.isFailure()) {
                            result = Try.failure(new IllegalArgumentException(result.failed().get().getMessage() + ", alternatively " + readerResult.failed().get().getMessage()));
                        }
                    }
                }
                return result;
            }
        };
    }

    
    /**
     * Returns an JSONProtocol that uses an AlternativesProtocol for reading, and always picks the first alternative when writing.
     */
    public static <T> JSONProtocol<T> readWrite(Seq<JSONProtocol<T>> alternatives) {
        JSONProtocol<T> write = alternatives.head();
        AlternativesProtocol<T> read = new AlternativesProtocol<T>(Seq.narrow(alternatives));
        return new JSONProtocol<T>() {
            @Override
            protected Try<T> empty() {
                return read.empty();
            }
            
            @Override
            public boolean isEmpty(T value) {
                return write.isEmpty(value);
            }
            
            @Override
            public Writer<T> writer() {
                return write.writer();
            }

            @Override
            public Reader<T> reader() {
                return read.reader();
            }
        };
    }
}

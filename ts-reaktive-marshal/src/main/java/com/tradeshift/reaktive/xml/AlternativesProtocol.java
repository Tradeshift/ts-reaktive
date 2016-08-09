package com.tradeshift.reaktive.xml;

import javax.xml.stream.events.XMLEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javaslang.collection.Seq;
import javaslang.control.Try;

/**
 * Forwards read events to multiple alternative protocols, emitting whenever any of the alternatives emit. If multiple 
 * alternatives emit for the same event, the first one wins.
 */
public class AlternativesProtocol<T> extends XMLReadProtocol<T> {
    private static final Logger log = LoggerFactory.getLogger(AlternativesProtocol.class);
    
    private final Seq<XMLReadProtocol<T>> alternatives;

    public AlternativesProtocol(Seq<XMLReadProtocol<T>> alternatives) {
        this.alternatives = alternatives;
    }

    @Override
    public Reader<T> reader() {
        Seq<Reader<T>> readers = alternatives.map(p -> p.reader());
        return new Reader<T>() {
            @Override
            public void reset() {
                readers.forEach(r -> r.reset());
            }

            @Override
            public Try<T> apply(XMLEvent evt) {
                Try<T> result = none();
                for (Reader<T> reader: readers) {
                    Try<T> readerResult = reader.apply(evt);
                    log.debug("reader {} said {}", reader, readerResult);
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
     * Returns an XMLProtocol that uses an AlternativesProtocol for reading, and always picks the first alternative when writing.
     */
    public static <T> XMLProtocol<T> readWrite(Seq<XMLProtocol<T>> alternatives) {
        XMLProtocol<T> write = alternatives.head();
        AlternativesProtocol<T> read = new AlternativesProtocol<T>(Seq.narrow(alternatives));
        return new XMLProtocol<T>() {
            @Override
            public Writer<T> writer() {
                return write.writer();
            }

            @Override
            public boolean isAttributeProtocol() {
                return write.isAttributeProtocol();
            }

            @Override
            public Reader<T> reader() {
                return read.reader();
            }
        };
    }
}

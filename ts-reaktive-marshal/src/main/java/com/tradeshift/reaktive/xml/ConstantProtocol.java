package com.tradeshift.reaktive.xml;

import javax.xml.stream.events.XMLEvent;

import javaslang.control.Try;

/**
 * Protocol that always writes the same value, and on reading emits "Present" when the expected value was indeed read.
 */
public class ConstantProtocol {
    /**
     * Marker type to indicate that the constant was indeed found in the incoming event stream (during reading).
     * During writing, the constant is always output whenever the writer is invoked.
     */
    public static class Present { private Present() {} }
    public static final Present PRESENT = new Present();
    
    public static <T> XMLReadProtocol<Present> read(XMLReadProtocol<T> inner, T value) {
        return new XMLReadProtocol<Present>() {
            @Override
            public Reader<Present> reader() {
                Reader<T> innerReader = inner.reader();
                
                return new Reader<Present>() {
                    @Override
                    public void reset() {
                        innerReader.reset();
                    }

                    @Override
                    public Try<Present> apply(XMLEvent evt) {
                        return innerReader.apply(evt).filter(value::equals).map(t -> PRESENT);
                    }
                };
            }
        };
    }

    public static <T> XMLWriteProtocol<Present> write(XMLWriteProtocol<T> inner, T value) {
        return new XMLWriteProtocol<Present>() {
            @Override
            public Writer<Present> writer() {
                return v -> inner.writer().apply(value);
            }

            @Override
            public boolean isAttributeProtocol() {
                return inner.isAttributeProtocol();
            }
        };
    }

    public static <T> XMLProtocol<Present> readWrite(XMLProtocol<T> inner, T value) {
        XMLReadProtocol<Present> read = read(inner, value);
        XMLWriteProtocol<Present> write = write(inner, value);
        return new XMLProtocol<Present>() {

            @Override
            public Writer<Present> writer() {
                return write.writer();
            }

            @Override
            public boolean isAttributeProtocol() {
                return write.isAttributeProtocol();
            }

            @Override
            public Reader<Present> reader() {
                return read.reader();
            }
        };
    }
}

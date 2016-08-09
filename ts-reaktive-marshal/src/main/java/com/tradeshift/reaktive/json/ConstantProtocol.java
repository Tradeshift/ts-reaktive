package com.tradeshift.reaktive.json;

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

    public static <T> JSONReadProtocol<Present> read(JSONReadProtocol<T> inner, T value) {
        return new JSONReadProtocol<Present>() {
            @Override
            public Reader<Present> reader() {
                Reader<T> innerReader = inner.reader();
                
                return new Reader<Present>() {
                    @Override
                    public Try<Present> reset() {
                        return emit(innerReader.reset());
                    }
                    
                    @Override
                    public Try<Present> apply(JSONEvent evt) {
                        return emit(innerReader.apply(evt));
                    }

                    private Try<Present> emit(Try<T> t) {
                        return t.filter(value::equals).map(v -> PRESENT);
                    }
                };
            }
            
            @Override
            public String toString() {
                return inner.toString() + " with value " + value;
            }
        };
    }
    
    public static <T> JSONWriteProtocol<Present> write(JSONWriteProtocol<T> inner, T value) {
        return new JSONWriteProtocol<Present>() {
            private final Writer<Present> writer = p -> inner.writer().apply(value);

            @Override
            public Writer<Present> writer() {
                return writer;
            }
            
            @Override
            public String toString() {
                return inner.toString() + " with value " + value;
            }
        };
    }
    
    public static <T> JSONProtocol<Present> readWrite(JSONProtocol<T> inner, T value) {
        JSONReadProtocol<Present> read = read(inner, value);
        JSONWriteProtocol<Present> write = write(inner, value);
        return new JSONProtocol<Present>() {
            @Override
            public Writer<Present> writer() {
                return write.writer();
            }

            @Override
            public Reader<Present> reader() {
                return read.reader();
            }
            
            @Override
            public String toString() {
                return inner.toString() + " with value " + value;
            }
        };
    }
}

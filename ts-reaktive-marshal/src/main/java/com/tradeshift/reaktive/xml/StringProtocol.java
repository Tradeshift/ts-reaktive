package com.tradeshift.reaktive.xml;

import java.util.NoSuchElementException;

import com.tradeshift.reaktive.Regex;
import com.tradeshift.reaktive.marshal.StringMarshallable;

import javaslang.Function1;
import javaslang.control.Try;

public abstract class StringProtocol extends XMLProtocol<String> {
    /**
     * Converts the body to a different type.
     */
    public <T> XMLProtocol<T> as(StringMarshallable<T> type) {
        return new StringMarshallableProtocol<>(type, this);
    }
    
    /**
     * Returns an XMLProtocol that only reads strings that match the given regular expression. The resulting
     * protocol can not be used for writing.
     */
    public <T> XMLReadProtocol<T> matching(Regex<T> regex) {
        StringProtocol parent = this;
        return new XMLReadProtocol<T>() {
            @Override
            public Reader<T> reader() {
                return parent.reader().flatMap(s -> regex.match(s).toTry());
            }
        };
    }

    /**
     * Returns an XMLProtocol that only reads strings that match the given regular expression. 
     * During writing, the given function is called. It's the callers responsibility to ensure that
     * the result of the function matches the regex.
     */
    public <T> XMLProtocol<T> matching(Regex<T> regex, Function1<T,String> onWrite) {
        StringProtocol parent = this;
        return new XMLProtocol<T>() {
            @Override
            public Reader<T> reader() {
                return parent.reader().flatMap(s -> regex.match(s).toTry().orElse(() -> Try.failure(new NoSuchElementException("Should match " + regex))));
            }

            @Override
            public Writer<T> writer() {
                return parent.writer().compose(onWrite);
            }
            
            @Override
            public boolean isAttributeProtocol() {
                return parent.isAttributeProtocol();
            }
        };
    }

}

package com.tradeshift.reaktive.json;

import com.tradeshift.reaktive.Regex;
import com.tradeshift.reaktive.marshal.ValidationException;

import javaslang.Function1;
import javaslang.control.Try;

public class StringValueProtocol extends ValueProtocol<String> {
    public static StringValueProtocol INSTANCE = new StringValueProtocol();
    
    private StringValueProtocol() {
        super("(string)",
            evt -> Try.success(evt.getValueAsString()), 
            s -> new JSONEvent.StringValue(s));
    }
    
    /**
     * Returns a JSONProtocol that only reads strings that match the given regular expression. The resulting
     * protocol can not be used for writing.
     */
    public <T> JSONReadProtocol<T> matching(Regex<T> regex) {
        StringValueProtocol parent = this;
        return new JSONReadProtocol<T>() {
            @Override
            public Reader<T> reader() {
                return parent.reader().flatMap(s -> regex.match(s).toTry());
            }
        };
    }

    /**
     * Returns a JSONProtocol that only reads strings that match the given regular expression. 
     * During writing, the given function is called. It's the callers responsibility to ensure that
     * the result of the function matches the regex.
     */
    public <T> JSONProtocol<T> matching(Regex<T> regex, Function1<T,String> onWrite) {
        StringValueProtocol parent = this;
        return new JSONProtocol<T>() {
            @Override
            public Reader<T> reader() {
                return parent.reader().flatMap(s -> regex.match(s).toTry().orElse(() -> Try.failure(new ValidationException("Should match " + regex))));
            }

            @Override
            public Writer<T> writer() {
                return parent.writer().compose(onWrite);
            }
            
        };
    }
}

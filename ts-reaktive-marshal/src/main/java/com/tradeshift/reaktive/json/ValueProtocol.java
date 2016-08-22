package com.tradeshift.reaktive.json;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tradeshift.reaktive.json.JSONEvent.Value;

import javaslang.Function1;
import javaslang.control.Try;

public class ValueProtocol<T> extends JSONProtocol<T> {
    // Only numeric and boolean types are defined here, since they have to marshal to numbers and booleans in JSON. 
    // For everything that marshals to strings, re-use StringMarshallable.* using JSONProtocol.as(XXX, ...)
    
    /** A Java integer represented as a JSON number (on reading, JSON string is also allowed) */
    public static final ValueProtocol<Integer> INTEGER = of("(signed 32-bit integer)",
        evt -> Try.of(() -> Integer.parseInt(evt.getValueAsString())), 
        i -> new JSONEvent.NumericValue(String.valueOf(i)));
    
    /** A Java long represented as a JSON number (on reading, JSON string is also allowed) */
    public static final ValueProtocol<Long> LONG = of("(signed 64-bit integer)",
        evt -> Try.of(() -> Long.parseLong(evt.getValueAsString())), 
        l -> new JSONEvent.NumericValue(String.valueOf(l)));

    /** A Java big decimal represented as a JSON number (on reading, JSON string is also allowed) */
    public static final ValueProtocol<BigDecimal> BIGDECIMAL = of("(arbitrary precision decimal)",
        evt -> Try.of(() -> new BigDecimal(evt.getValueAsString())), 
        d -> new JSONEvent.NumericValue(String.valueOf(d)));
    
    /** A Java big integer represented as a JSON number (on reading, JSON string is also allowed) */
    public static final ValueProtocol<BigInteger> BIGINTEGER = of("(arbitrary precision integer)",
        evt -> Try.of(() -> new BigInteger(evt.getValueAsString())), 
        d -> new JSONEvent.NumericValue(String.valueOf(d)));
    
    /** A Java boolean represented a JSON boolean (on reading, a JSON string of "true" or "false" is also allowed) */ 
    public static final JSONProtocol<Boolean> BOOLEAN = of("(boolean)", 
        v -> Try.of(() -> v.getValueAsString().equals("true")), 
        b -> b ? JSONEvent.TRUE : JSONEvent.FALSE);
    
    private static final Logger log = LoggerFactory.getLogger(ValueProtocol.class);
    
    private final Function1<Value, Try<T>> tryRead;
    private final Function1<T,Value> write;
    private final String description;
    
    public static <T> ValueProtocol<T> of(String description, Function1<Value, Try<T>> tryRead, Function1<T, Value> write) {
        return new ValueProtocol<>(description, tryRead, write);
    }
    
    protected ValueProtocol(String description, Function1<Value, Try<T>> tryRead, Function1<T, Value> write) {
        this.description = description;
        this.tryRead = tryRead;
        this.write = write;
    }

    private final Writer<T> writer = new Writer<T>() {
        @Override
        public Stream<JSONEvent> apply(T value) {
            return Stream.of(write.apply(value));
        }
    };

    @Override
    public Reader<T> reader() {
        return new Reader<T>() {
            private int level = 0;
            
            @Override
            public Try<T> reset() {
                level = 0;
                return none();
            }

            @Override
            public Try<T> apply(JSONEvent evt) {
                if (evt == JSONEvent.START_OBJECT || evt == JSONEvent.START_ARRAY) {
                    level++;
                } else if (evt == JSONEvent.END_OBJECT || evt == JSONEvent.END_ARRAY) {
                    level--;
                }
                
                if (level == 0 && evt instanceof JSONEvent.Value) {
                    Try<T> result = tryRead.apply(JSONEvent.Value.class.cast(evt));
                    log.info("Read {}", result);
                    return result;
                } else {
                    return none();
                }
            }

        };
    }

    @Override
    public Writer<T> writer() {
        return writer;
    }

    @Override
    public String toString() {
        return description;
    }
}

package com.tradeshift.reaktive.json;

import java.util.stream.Stream;

import javaslang.control.Option;
import javaslang.control.Try;

public class OptionProtocol {
    public static <T> JSONReadProtocol<Option<T>> read(JSONReadProtocol<T> inner) {
        
        return new JSONReadProtocol<Option<T>>() {
            @Override
            public Reader<Option<T>> reader() {
                return inner.reader().map(Option::some);
            }

            @Override
            protected Try<Option<T>> empty() {
                return Try.success(Option.none());
            }
            
            @Override
            public String toString() {
                return "option(" + inner + ")";
            }
        };
    }
    
    public static <T> JSONWriteProtocol<Option<T>> write(JSONWriteProtocol<T> inner) {
        return new JSONWriteProtocol<Option<T>>() {
            @Override
            public Writer<Option<T>> writer() {
                Writer<T> parentWriter = inner.writer();
                return value -> value.map(parentWriter::apply).getOrElse(Stream.empty());
            }
            
            @Override
            public boolean isEmpty(Option<T> value) {
                return value.isEmpty();
            }
            
            @Override
            public String toString() {
                return "option(" + inner + ")";
            }
        };        
    }
    
    public static <T> JSONProtocol<Option<T>> readWrite(JSONProtocol<T> inner) {
        JSONReadProtocol<Option<T>> read = read(inner);
        JSONWriteProtocol<Option<T>> write = write(inner);
        return new JSONProtocol<Option<T>>() {
            @Override
            public Writer<Option<T>> writer() {
                return write.writer();
            }

            @Override
            public Reader<Option<T>> reader() {
                return read.reader();
            }
            
            @Override
            protected Try<Option<T>> empty() {
                return read.empty();
            }
            
            @Override
            public boolean isEmpty(Option<T> value) {
                return value.isEmpty();
            }
            
            /*
            @Override
            protected Try<Option<T>> combine(Try<Option<T>> previous, Try<Option<T>> current) {
                return read.combine(previous, current);
            }
            */
            
            @Override
            public String toString() {
                return "option(" + inner + ")";
            }
        };        
    }
}

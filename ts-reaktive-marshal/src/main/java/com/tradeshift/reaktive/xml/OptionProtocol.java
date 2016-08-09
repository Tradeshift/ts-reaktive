package com.tradeshift.reaktive.xml;

import java.util.stream.Stream;

import javaslang.control.Option;
import javaslang.control.Try;

public class OptionProtocol {
    public static <T> XMLReadProtocol<Option<T>> read(XMLReadProtocol<T> inner) {
        return new XMLReadProtocol<Option<T>>() {
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

    public static <T> XMLWriteProtocol<Option<T>> write(XMLWriteProtocol<T> inner) {
        return new XMLWriteProtocol<Option<T>>() {
            @Override
            public Writer<Option<T>> writer() {
                Writer<T> parentWriter = inner.writer();
                return value -> value.map(parentWriter::apply).getOrElse(Stream.empty());
            }
            
            @Override
            public boolean isAttributeProtocol() {
                return inner.isAttributeProtocol();
            }
            
            @Override
            public String toString() {
                return "option(" + inner + ")";
            }            
        };
    }
    
    public static <T> XMLProtocol<Option<T>> readWrite(XMLProtocol<T> inner) {
        XMLReadProtocol<Option<T>> read = read(inner);
        XMLWriteProtocol<Option<T>> write = write(inner);
        return new XMLProtocol<Option<T>>() {
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
            protected Try<Option<T>> combine(Try<Option<T>> previous, Try<Option<T>> current) {
                return read.combine(previous, current);
            }
            
            @Override
            public boolean isAttributeProtocol() {
                return write.isAttributeProtocol();
            }
            
            @Override
            public String toString() {
                return "option(" + inner + ")";
            }            
        };
    }
}

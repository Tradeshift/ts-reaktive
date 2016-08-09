package com.tradeshift.reaktive.xml;

import javaslang.Tuple2;

public class StringStringProtocol extends TStringProtocol<String> {
    private final XMLProtocol<Tuple2<String, String>> delegate;

    public StringStringProtocol(XMLProtocol<Tuple2<String, String>> delegate) {
        this.delegate = delegate;
    }

    public com.tradeshift.reaktive.xml.XMLWriteProtocol.Writer<Tuple2<String, String>> writer() {
        return delegate.writer();
    }

    public boolean isAttributeProtocol() {
        return delegate.isAttributeProtocol();
    }

    public com.tradeshift.reaktive.xml.XMLReadProtocol.Reader<Tuple2<String, String>> reader() {
        return delegate.reader();
    }

    public int hashCode() {
        return delegate.hashCode();
    }

    public boolean equals(Object obj) {
        return delegate.equals(obj);
    }

    public String toString() {
        return delegate.toString();
    }
}

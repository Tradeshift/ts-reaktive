package com.tradeshift.reaktive.xml;

import javax.xml.namespace.QName;

import javaslang.Tuple2;

public class QNameStringProtocol extends TStringProtocol<QName> {
    private final XMLProtocol<Tuple2<QName, String>> delegate;

    public QNameStringProtocol(XMLProtocol<Tuple2<QName, String>> delegate) {
        this.delegate = delegate;
    }

    public com.tradeshift.reaktive.xml.XMLWriteProtocol.Writer<Tuple2<QName, String>> writer() {
        return delegate.writer();
    }

    public boolean isAttributeProtocol() {
        return delegate.isAttributeProtocol();
    }

    public com.tradeshift.reaktive.xml.XMLReadProtocol.Reader<Tuple2<QName, String>> reader() {
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
    
    /**
     * Returns the protocol only reading and writing local names for _1, without namespace.
     */
    public StringStringProtocol asLocalName() {
        return new StringStringProtocol(this.map(t -> t.map1(QName::getLocalPart), t -> t.map1(QName::new)));
    }
}

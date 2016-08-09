package com.tradeshift.reaktive.xml;

import com.tradeshift.reaktive.marshal.StringMarshallable;

public class StringMarshallableProtocol<T> extends XMLProtocol<T> {
    private final StringMarshallable<T> type;
    private final XMLProtocol<String> delegate;
    
    public StringMarshallableProtocol(StringMarshallable<T> type, XMLProtocol<String> delegate) {
        this.type = type;
        this.delegate = delegate;
    }

    @Override
    public Writer<T> writer() {
        return delegate.writer().compose(type::write);
    }

    @Override
    public Reader<T> reader() {
        return delegate.reader().flatMap(s -> type.tryRead(s));
    }

    @Override
    public boolean isAttributeProtocol() {
        return delegate.isAttributeProtocol();
    }
}

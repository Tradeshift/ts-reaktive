package com.tradeshift.reaktive.json;

import com.tradeshift.reaktive.marshal.StringMarshallable;

public class StringMarshallableProtocol<T> extends JSONProtocol<T> {
    private final StringMarshallable<T> type;
    private final JSONProtocol<String> delegate;
    
    public StringMarshallableProtocol(StringMarshallable<T> type, JSONProtocol<String> delegate) {
        this.type = type;
        this.delegate = delegate;
    }

    @Override
    public Reader<T> reader() {
        return delegate.reader().flatMap(s -> type.tryRead(s));
    }
    
    @Override
    public Writer<T> writer() {
        return delegate.writer().compose(type::write);
    }
}

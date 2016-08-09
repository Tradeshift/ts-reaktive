package com.tradeshift.reaktive.xml;

import java.util.List;
import java.util.function.Function;

import javax.xml.namespace.QName;

import javaslang.Function1;
import javaslang.collection.Vector;
import javaslang.control.Option;

public class TagProtocol<T> extends XMLProtocol<T> {
    private final TagReadProtocol<T> read;
    private final TagWriteProtocol<T> write;
   
    public TagProtocol(Option<QName> name, Vector<XMLProtocol<?>> protocols, Function<List<?>, T> produce, Vector<Function1<T, ?>> getters) {
        this.read = new TagReadProtocol<T>(name, protocols, produce, Vector.empty());
        this.write = new TagWriteProtocol<T>(name, protocols, getters);
    }
    
    private TagProtocol(TagReadProtocol<T> read, TagWriteProtocol<T> write) {
        this.read = read;
        this.write = write;
    }

    @Override
    public Writer<T> writer() {
        return write.writer();
    }

    @Override
    public Reader<T> reader() {
        return read.reader();
    }
    
    @Override
    public boolean isAttributeProtocol() {
        return false;
    }

    public <U> TagProtocol<T> having(XMLProtocol<U> nestedProtocol, U value) {
        return new TagProtocol<T>(read.having(nestedProtocol, value), write.having(nestedProtocol, value)); 
    }
}
 
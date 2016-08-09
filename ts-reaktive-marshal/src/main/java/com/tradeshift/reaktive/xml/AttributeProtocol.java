package com.tradeshift.reaktive.xml;

import java.util.stream.Stream;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.XMLEvent;

import javaslang.control.Try;

/**
 * Handles reading and writing a single attribute of a tag. 
 */
public class AttributeProtocol extends StringProtocol {
    private static final XMLEventFactory factory = XMLEventFactory.newFactory();
    
    private final QName name;
    private final Writer<String> writer;

    public AttributeProtocol(QName name) {
        this.name = name;
        this.writer = s -> Stream.of(factory.createAttribute(name, s));
    }
    
    @Override
    public boolean isAttributeProtocol() {
        return true;
    }

    @Override
    public Reader<String> reader() {
        return new Reader<String>() {
            private int level = 0;

            @Override
            public void reset() {
                level = 0;
            }

            @Override
            public Try<String> apply(XMLEvent evt) {
                if (level == 0 && evt.isAttribute() && matches(Attribute.class.cast(evt))) {
                    return Try.success(Attribute.class.cast(evt).getValue());
                } else if (evt.isStartElement()) {
                    level++;
                    return none();
                } else if (evt.isEndElement()) {
                    level--;
                    return none();
                } else {
                    return none();
                }
            }

            private boolean matches(Attribute attr) {
                return name.equals(attr.getName());
            }
            
        };
    }
    
    @Override
    public Writer<String> writer() {
        return writer;
    }
}

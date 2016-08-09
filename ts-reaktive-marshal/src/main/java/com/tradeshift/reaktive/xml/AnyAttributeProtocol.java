package com.tradeshift.reaktive.xml;

import java.util.stream.Stream;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.XMLEvent;

import javaslang.Tuple;
import javaslang.Tuple2;
import javaslang.control.Try;

/**
 * Handles reading and writing a single attribute of a tag, matching any name 
 */
public class AnyAttributeProtocol extends XMLProtocol<Tuple2<QName,String>> {
    public static final AnyAttributeProtocol INSTANCE = new AnyAttributeProtocol();
    
    private static final XMLEventFactory factory = XMLEventFactory.newFactory();
    
    private final Writer<Tuple2<QName,String>> writer;

    private AnyAttributeProtocol() {
        this.writer = t -> Stream.of(factory.createAttribute(t._1(), t._2()));
    }
    
    @Override
    public boolean isAttributeProtocol() {
        return true;
    }

    @Override
    public Reader<Tuple2<QName,String>> reader() {
        return new Reader<Tuple2<QName,String>>() {
            private int level = 0;

            @Override
            public void reset() {
                level = 0;
            }

            @Override
            public Try<Tuple2<QName,String>> apply(XMLEvent evt) {
                if (level == 0 && evt.isAttribute()) {
                    Attribute attr = Attribute.class.cast(evt);
                    return Try.success(Tuple.of(attr.getName(), attr.getValue()));
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
        };
    }
    
    @Override
    public Writer<Tuple2<QName,String>> writer() {
        return writer;
    }
}

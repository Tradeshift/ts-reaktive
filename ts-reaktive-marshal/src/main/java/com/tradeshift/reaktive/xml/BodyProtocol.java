package com.tradeshift.reaktive.xml;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.events.XMLEvent;

import javaslang.control.Try;

public class BodyProtocol extends StringProtocol {
    public static final BodyProtocol INSTANCE = new BodyProtocol();
    
    private static final XMLEventFactory factory = XMLEventFactory.newFactory();
    
    private final Writer<String> writer;

    private BodyProtocol() {
        this.writer = value -> Stream.of(factory.createCharacters(value));
    }

    @Override
    public Writer<String> writer() {
        return writer;
    }

    @Override
    public Reader<String> reader() {
        return new Reader<String>() {
            private int level = 0;
            private final List<String> buffer = new ArrayList<>();
            
            @Override
            public void reset() {
                level = 0;
                buffer.clear();
            }

            @Override
            public Try<String> apply(XMLEvent evt) {
                if (level == 0 && evt.isCharacters()) {
                    buffer.add(evt.asCharacters().getData());
                    Try<String> result = Try.success(buffer.stream().collect(Collectors.joining()));
                    buffer.clear();
                    return result;
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
    public boolean isAttributeProtocol() {
        return false;
    }
}

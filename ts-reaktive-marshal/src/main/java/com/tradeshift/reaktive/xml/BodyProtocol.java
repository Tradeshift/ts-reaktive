package com.tradeshift.reaktive.xml;

import static com.tradeshift.reaktive.marshal.ReadProtocol.none;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.events.XMLEvent;

import com.tradeshift.reaktive.marshal.Protocol;
import com.tradeshift.reaktive.marshal.Reader;
import com.tradeshift.reaktive.marshal.StringProtocol;
import com.tradeshift.reaktive.marshal.Writer;

import javaslang.control.Try;

/**
 * Represents the character data body at root level as a String. If during reading, an empty tag or no body is encountered,
 * no value is emitted. In other words, no empty strings will be emitted during reading.
 */
public class BodyProtocol extends StringProtocol<XMLEvent> {
    public static final BodyProtocol INSTANCE = new BodyProtocol();
    
    private static final XMLEventFactory factory = XMLEventFactory.newFactory();
    
    private BodyProtocol() {
        super(new Protocol<XMLEvent,String>(){
            Writer<XMLEvent,String> writer = value -> Stream.of(factory.createCharacters(value));
            
            @Override
            public Writer<XMLEvent,String> writer() {
                return writer;
            }
            
            @Override
            public Class<? extends XMLEvent> getEventType() {
                return XMLEvent.class;
            }

            @Override
            public Reader<XMLEvent,String> reader() {
                return new Reader<XMLEvent,String>() {
                    private int level = 0;
                    private final List<String> buffer = new ArrayList<>();
                    
                    @Override
                    public Try<String> reset() {
                        level = 0;
                        Try<String> result = Try.success(buffer.stream().collect(Collectors.joining()));
                        buffer.clear();
                        return result.get().isEmpty() ? none() : result;
                    }

                    @Override
                    public Try<String> apply(XMLEvent evt) {
                        if (level == 0 && evt.isCharacters()) {
                            buffer.add(evt.asCharacters().getData());
                            return none();
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
            public String toString() {
                return "body";
            }
        }, XMLProtocol.locator);
    }
}

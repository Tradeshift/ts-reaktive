package com.tradeshift.reaktive.xml;

import static com.tradeshift.reaktive.marshal.ReadProtocol.none;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.XMLEvent;

import com.tradeshift.reaktive.marshal.Protocol;
import com.tradeshift.reaktive.marshal.Reader;
import com.tradeshift.reaktive.marshal.StringProtocol;
import com.tradeshift.reaktive.marshal.Writer;

import io.vavr.collection.Vector;
import io.vavr.control.Try;

/**
 * Handles reading and writing a single attribute of a tag.
 */
public class AttributeProtocol extends StringProtocol<XMLEvent> {
    private static final XMLEventFactory factory = XMLEventFactory.newFactory();
    
    public AttributeProtocol(QName name) {
        super(new Protocol<XMLEvent,String>() {
            Writer<XMLEvent,String> writer = Writer.of(s -> Vector.of(factory.createAttribute(name, s)));
            @Override
            public Class<? extends XMLEvent> getEventType() {
                return Attribute.class;
            }
            
            @Override
            public String toString() {
                return "@" + name;
            }

            @Override
            public Reader<XMLEvent,String> reader() {
                return new Reader<XMLEvent,String>() {
                    private int level = 0;

                    @Override
                    public Try<String> reset() {
                        level = 0;
                        return none();
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
            public Writer<XMLEvent,String> writer() {
                return writer;
            }
        }, XMLProtocol.locator);
    }
}

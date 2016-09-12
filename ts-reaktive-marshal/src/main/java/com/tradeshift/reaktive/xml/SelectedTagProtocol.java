package com.tradeshift.reaktive.xml;

import javax.xml.namespace.QName;
import javax.xml.stream.events.XMLEvent;

import com.tradeshift.reaktive.marshal.Protocol;
import com.tradeshift.reaktive.marshal.ReadProtocol;
import com.tradeshift.reaktive.marshal.Reader;
import com.tradeshift.reaktive.marshal.Writer;

import javaslang.control.Try;

/**
 * Matches an exact XML tag, and emits the tag itself and any sub-events that make up its body.
 */
public class SelectedTagProtocol implements Protocol<XMLEvent, XMLEvent> {
    public static SelectedTagProtocol tag(QName name) {
        return new SelectedTagProtocol(name);
    }
    
    private final QName name;

    public SelectedTagProtocol(QName name) {
        this.name = name;
    }

    @Override
    public Reader<XMLEvent, XMLEvent> reader() {
        return new Reader<XMLEvent, XMLEvent>() {
            private boolean matched = false;
            private int level = 0;

            @Override
            public Try<XMLEvent> reset() {
                matched = false;
                level = 0;
                return ReadProtocol.none();
            }

            @Override
            public Try<XMLEvent> apply(XMLEvent event) {
                if (level == 0) {
                    if (event.isStartElement() && event.asStartElement().getName().equals(name)) {
                        level++;
                        matched = true;
                        return Try.success(event);
                    } else if (event.isStartElement()) {
                        level++;
                        return ReadProtocol.none();
                    } else {
                        return ReadProtocol.none();
                    }
                } else if (matched && level == 1 && event.isEndElement()) {
                    level--;
                    matched = false;
                    return Try.success(event);
                } else {
                    if (event.isStartElement()) {
                        level++;
                    } else if (event.isEndElement()) {
                        level--;
                    }
                    
                    return (matched) ? Try.success(event) : ReadProtocol.none();
                }
            }
        };
    }

    @Override
    public Class<? extends XMLEvent> getEventType() {
        return XMLEvent.class;
    }

    @Override
    public Writer<XMLEvent, XMLEvent> writer() {
        // Since the start/end tag events themselves are part of the protocol, we can simply echo all events back.
        return Writer.identity();
    }
    
    
}

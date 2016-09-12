package com.tradeshift.reaktive.xml;

import static com.tradeshift.reaktive.marshal.ReadProtocol.none;

import javax.xml.stream.events.Characters;
import javax.xml.stream.events.XMLEvent;

import com.tradeshift.reaktive.marshal.Protocol;
import com.tradeshift.reaktive.marshal.Reader;
import com.tradeshift.reaktive.marshal.Writer;

import javaslang.control.Try;

/**
 * Protocol that emits the body of a tag as actual {@link Characters} events. This has the advantage that the body
 * can be streamed, but the disadvantage that you can't directly map it to other types.
 */
public class BodyEventsProtocol implements Protocol<XMLEvent, Characters> {
    public static final BodyEventsProtocol INSTANCE = new BodyEventsProtocol();

    @Override
    public Reader<XMLEvent, Characters> reader() {
        return new Reader<XMLEvent,Characters>() {
            private int level = 0;
            
            @Override
            public Try<Characters> reset() {
                level = 0;
                return none();
            }

            @Override
            public Try<Characters> apply(XMLEvent evt) {
                if (level == 0 && evt.isCharacters()) {
                    return Try.success(evt.asCharacters());
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
    public Class<? extends XMLEvent> getEventType() {
        return Characters.class;
    }

    @Override
    public Writer<XMLEvent, Characters> writer() {
        return Writer.identity();
    }

}

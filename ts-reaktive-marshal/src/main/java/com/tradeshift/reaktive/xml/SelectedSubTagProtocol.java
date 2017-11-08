package com.tradeshift.reaktive.xml;

import javax.xml.namespace.QName;
import javax.xml.stream.events.XMLEvent;

import com.tradeshift.reaktive.marshal.ReadProtocol;
import com.tradeshift.reaktive.marshal.Reader;

import io.vavr.collection.HashSet;
import io.vavr.collection.Set;
import io.vavr.control.Try;

/**
 * Matches an exact XML tag (at the current level or as any sub-tag), and emits the tag itself and any sub-events that make up its body.
 *
 * This is a tweaked version of SelectedTagProtocol, matching a series of tag names at any level.
 * This is only a ReadProtocol, since tags are skipped over that can't be put back when writing.
 */
public class SelectedSubTagProtocol implements ReadProtocol<XMLEvent, XMLEvent> {
    private final Set<QName> names;

    protected SelectedSubTagProtocol(Set<QName> names) {
        this.names = names;
    }

    @Override
    public Reader<XMLEvent, XMLEvent> reader() {
        return new Reader<XMLEvent, XMLEvent>() {
            private boolean matched = false;
            private int level = 0;
            private int matchLevel = -1;

            @Override
            public Try<XMLEvent> reset() {
                matched = false;
                level = 0;
                matchLevel = -1;
                return ReadProtocol.none();
            }

            @Override
            public Try<XMLEvent> apply(XMLEvent event) {
                if (!matched && event.isStartElement() && names.contains(event.asStartElement().getName())) {
                    matchLevel = level;
                    level++;
                    matched = true;
                    return Try.success(event);
                } else if (matched && event.isEndElement() && level == matchLevel + 1) {
                    level--;
                    matchLevel = -1;
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
    public String toString() {
        return "<" + names.mkString(" || ") + ">";
    }
}

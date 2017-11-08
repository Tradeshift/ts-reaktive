package com.tradeshift.reaktive.xml;

import static com.tradeshift.reaktive.xml.XMLProtocol.anySubTag;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.cuppa.Cuppa.describe;
import static org.forgerock.cuppa.Cuppa.it;

import javax.xml.namespace.QName;
import javax.xml.stream.events.XMLEvent;

import org.forgerock.cuppa.junit.CuppaRunner;
import org.junit.runner.RunWith;

import com.tradeshift.reaktive.xml.Stax;

import io.vavr.collection.Vector;

@RunWith(CuppaRunner.class)
public class SelectedSubTagProtocolSpec {
    private final SelectedSubTagProtocol proto = anySubTag(new QName("ns", "tag1"), new QName("ns", "tag2"));
    {
        describe("SelectedSubTagProtocol", () -> {
            it("should select a matched tag and its body if it's the root", () -> {
                Vector<XMLEvent> events = new Stax().parse("<tag1 xmlns='ns'>Hello</tag1>", proto.reader()).collect(Vector.collector());
                assertThat(events).hasSize(3);
                assertThat(events.apply(0).asStartElement().getName()).isEqualTo(new QName("ns", "tag1"));
                assertThat(events.apply(1).asCharacters().getData()).isEqualTo("Hello");
                assertThat(events.apply(2).asEndElement().getName()).isEqualTo(new QName("ns", "tag1"));
            });

            it("should select a matched tag and its body when occurring as several sub-tags", () -> {
                Vector<XMLEvent> events = new Stax().parse("<root><tag1 xmlns='ns'>Hello</tag1><other/><tag2 xmlns='ns'>World</tag2></root>", proto.reader()).collect(Vector.collector());
                assertThat(events).hasSize(6);
                assertThat(events.apply(0).asStartElement().getName()).isEqualTo(new QName("ns", "tag1"));
                assertThat(events.apply(1).asCharacters().getData()).isEqualTo("Hello");
                assertThat(events.apply(2).asEndElement().getName()).isEqualTo(new QName("ns", "tag1"));
                assertThat(events.apply(3).asStartElement().getName()).isEqualTo(new QName("ns", "tag2"));
                assertThat(events.apply(4).asCharacters().getData()).isEqualTo("World");
                assertThat(events.apply(5).asEndElement().getName()).isEqualTo(new QName("ns", "tag2"));
            });

            it("should not match the same tag name in another namespace", () -> {
                Vector<XMLEvent> events = new Stax().parse("<tag1 xmlns='another'>Hello</tag1>", proto.reader()).collect(Vector.collector());
                assertThat(events).isEmpty();
            });
        });
    }
}

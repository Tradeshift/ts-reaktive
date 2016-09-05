package com.tradeshift.reaktive.marshal.stream;

import java.util.ArrayList;
import java.util.List;

import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.events.XMLEvent;

import akka.NotUsed;
import akka.stream.javadsl.Flow;
import akka.util.ByteString;

/**
 * Serializes XMLEvents out as XML by invoking Stax, but in a non-blocking push/pull fashion,
 * only writing when there is demand from downstream.
 */
public class StaxWriter extends PushPullOutputStreamAdapter<List<XMLEvent>, XMLEventWriter> {
    private static final XMLOutputFactory factory = XMLOutputFactory.newInstance();
    
    /**
     * Returns a flow that buffers up to 100 XML events and writing them together.
     */
    public static Flow<XMLEvent,ByteString,NotUsed> flow() {
        return flow(100);
    }
    
    /**
     * Returns a flow that buffers up to [maximumBatchSize] XML events and writing them together.
     * 
     * Buffering is only done if upstream is faster than downstream. If there is demand from downstream,
     * also slower batches will be written.
     */
    public static Flow<XMLEvent,ByteString,NotUsed> flow(int maximumBatchSize) {
        return Flow.of(XMLEvent.class)
            .batch(maximumBatchSize, event -> {
                List<XMLEvent> l = new ArrayList<>();
                l.add(event);
                return l;
            }, (list, event) -> {
                list.add(event);
                return list;
            }).via(new StaxWriter());
    }
    
    private StaxWriter() {
        super(
            (attr, out) -> factory.createXMLEventWriter(out, "UTF-8"),
            (writer, events) -> {
                for (XMLEvent event: events) {
                    writer.add(event);
                }
                writer.flush();
            }
        );
    }
}

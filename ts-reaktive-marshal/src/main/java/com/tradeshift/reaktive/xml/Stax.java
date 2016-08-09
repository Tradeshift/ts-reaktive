package com.tradeshift.reaktive.xml;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tradeshift.reaktive.xml.XMLReadProtocol.Reader;
import com.tradeshift.reaktive.xml.XMLWriteProtocol.Writer;

import javaslang.control.Option;
import javaslang.control.Try;

/**
 * Interface to and from stax to the XML marshalling framework.
 * 
 * TODO re-implement in terms of XMLStreamWriter / XMLStreamReader, so that
 *   - real empty tags can be written
 *   - upgrading to Aalto XML is easier
 *   
 * TODO write another class that hooks in https://github.com/drewhk/akka-xml-stream/
 */
public class Stax {
    public static final Logger log = LoggerFactory.getLogger(Stax.class);
    
    private static final XMLInputFactory inFactory = XMLInputFactory.newFactory();
    private static final XMLOutputFactory outFactory = XMLOutputFactory.newFactory();

    public <T> String writeAsString(T obj, Writer<T> writer) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(obj, writer, out);
        return new String(out.toByteArray());
    }
    
    public <T> void write(T obj, Writer<T> writer, OutputStream out) {
        try {
            XMLEventWriter xmlW = outFactory.createXMLEventWriter(out);
            writer.apply(obj).forEach(evt -> {
                try {
                    log.debug("Writing {}", evt);
                    xmlW.add(evt);
                } catch (XMLStreamException e) {
                    throw new IllegalArgumentException(e);
                }
            });
        } catch (XMLStreamException | FactoryConfigurationError e) {
            throw new IllegalArgumentException(e);
        }
    }
    
    public <T> Stream<T> parse(String s, Reader<T> reader) {
        reader.reset();
        
        try {
            XMLEventReader in = inFactory.createXMLEventReader(new StringReader(s));
            Iterator<T> iterator = new Iterator<T>() {
                private Option<T> next = parse();

                private Option<T> parse() {
                    try {
                        while (in.peek() != null) {
                            Try<T> read = reader.apply(in.nextEvent());
                            if (read.isSuccess()) {
                                return read.toOption();
                            } else if (read.isFailure() && !XMLReadProtocol.isNone(read)) {
                                throw (RuntimeException) read.failed().get();
                            }
                        }
                        return Option.none();
                    } catch (XMLStreamException e) {
                        throw new IllegalArgumentException(e);
                    }
                }
                
                @Override
                public boolean hasNext() {
                    return next.isDefined();
                }

                @Override
                public T next() {
                    T elmt = next.get();
                    next = parse();
                    return elmt;
                }
            };
            
            return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED),
                false);
        } catch (XMLStreamException e) {
            throw new IllegalArgumentException(e);
        }
    }
}

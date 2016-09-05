package com.tradeshift.reaktive.xml;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
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
import javax.xml.stream.events.XMLEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tradeshift.reaktive.marshal.ReadProtocol;
import com.tradeshift.reaktive.marshal.Reader;
import com.tradeshift.reaktive.marshal.Writer;

import javaslang.control.Option;
import javaslang.control.Try;

/**
 * Interface to and from stax to the XML marshalling framework.
 */
public class Stax {
    public static final Logger log = LoggerFactory.getLogger(Stax.class);
    
    private static final XMLInputFactory inFactory = XMLInputFactory.newFactory();
    private static final XMLOutputFactory outFactory = XMLOutputFactory.newFactory();

    public <T> String writeAsString(T obj, Writer<XMLEvent,T> writer) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(obj, writer, out);
        return new String(out.toByteArray());
    }
    
    public <T> void write(T obj, Writer<XMLEvent,T> writer, OutputStream out) {
        try {
            XMLEventWriter xmlW = outFactory.createXMLEventWriter(out);
            writer.applyAndReset(obj).forEach(evt -> {
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
    
    public <T> Stream<T> parse(File f, Reader<XMLEvent,T> reader) {
        try {
            return parse(inFactory.createXMLEventReader(new BufferedInputStream(new FileInputStream(f))), reader);
        } catch (XMLStreamException | FileNotFoundException e) {
            throw new IllegalArgumentException(e);
        }
    }
    
    public <T> Stream<T> parse(InputStream in, Reader<XMLEvent,T> reader) {
        try {
            return parse(inFactory.createXMLEventReader(in), reader);
        } catch (XMLStreamException e) {
            throw new IllegalArgumentException(e);
        }
    }
    
    public <T> Stream<T> parse(String s, Reader<XMLEvent,T> reader) {
        try {
            return parse(inFactory.createXMLEventReader(new StringReader(s)), reader);
        } catch (XMLStreamException e) {
            throw new IllegalArgumentException(e);
        }
    }
    
    private <T> Stream<T> parse(XMLEventReader in, Reader<XMLEvent,T> reader) {
        reader.reset();
        
        Iterator<T> iterator = new Iterator<T>() {
            private Option<T> next = parse();

            private Option<T> parse() {
                try {
                    while (in.peek() != null) {
                        Try<T> read = reader.apply(in.nextEvent());
                        if (read.isSuccess()) {
                            return read.toOption();
                        } else if (read.isFailure() && !ReadProtocol.isNone(read)) {
                            throw (RuntimeException) read.failed().get();
                        }
                    }
                    Try<T> read = reader.reset();
                    if (read.isSuccess()) {
                        return read.toOption();
                    } else if (read.isFailure() && !ReadProtocol.isNone(read)) {
                        throw (RuntimeException) read.failed().get();
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
    }
}

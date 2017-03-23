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
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.xml.namespace.QName;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.Namespace;
import javax.xml.stream.events.ProcessingInstruction;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import com.tradeshift.reaktive.marshal.ReadProtocol;
import com.tradeshift.reaktive.marshal.Reader;
import com.tradeshift.reaktive.marshal.Writer;

import io.vavr.control.Option;
import io.vavr.control.Try;

/**
 * Interface to and from stax to the XML marshalling framework.
 * 
 * This class is mostly for testing, as you'll be using akka streams for real marshalling work,
 * why else wouldn't you bother with streaming data binding? :-)
 */
public class Stax {
    public static final Logger log = LoggerFactory.getLogger(Stax.class);
    
    // We want the JDK built-in STAX, even though Aalto may be on the classpath. That's because the built-in one does
    // give location information by default, and there's no generic way to configure that.
    private static final XMLInputFactory inFactory = instantiate("com.sun.xml.internal.stream.XMLInputFactoryImpl");
    private static final XMLOutputFactory outFactory = instantiate("com.sun.xml.internal.stream.XMLOutputFactoryImpl");

    public <T> String writeAllAsString(Stream<T> stream, Writer<XMLEvent, T> writer) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeAll(stream, writer, out);
        return new String(out.toByteArray());
    }
    
    public <T> String writeAsString(T obj, Writer<XMLEvent,T> writer) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(obj, writer, out);
        return new String(out.toByteArray());
    }
    
    public <T> void write(T obj, Writer<XMLEvent,T> writer, OutputStream out) {
        try {
            XMLEventWriter xmlW = outFactory.createXMLEventWriter(out);
            writer.applyAndReset(obj).forEach(addTo(xmlW));
        } catch (XMLStreamException | FactoryConfigurationError e) {
            throw new IllegalArgumentException(e);
        }
    }
    
    public <T> void writeAll(Stream<T> objs, Writer<XMLEvent,T> writer, OutputStream out) {
        try {
            XMLEventWriter xmlW = outFactory.createXMLEventWriter(out);
            objs.flatMap(obj -> writer.apply(obj).toJavaStream()).forEach(addTo(xmlW));
            writer.reset().forEach(addTo(xmlW));
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

    public static void apply(XMLEvent event, ContentHandler handler) throws SAXException {
        if (event.isEndDocument()) {
            handler.endDocument();
        } else if (event.isEndElement()) {
            EndElement e = event.asEndElement();
            handler.endElement(e.getName().getNamespaceURI(), e.getName().getLocalPart(), qname(e.getName()));
        } else if (event.isStartElement()) {
            StartElement e = event.asStartElement();
            AttributesImpl attr = new AttributesImpl();
            @SuppressWarnings("rawtypes") Iterator i = e.getAttributes();
            while (i.hasNext()) {
                Attribute a = (Attribute) i.next();
                attr.addAttribute(a.getName().getNamespaceURI(), a.getName().getLocalPart(), qname(a.getName()), "CDATA", a.getValue());
            }
            handler.startElement(e.getName().getNamespaceURI(), e.getName().getLocalPart(), qname(e.getName()), attr);
        } else if (event.isAttribute()) {
            // ignore, should be embedded into StartElement
        } else if (event.isCharacters()) {
            Characters e = event.asCharacters();
            String s = e.asCharacters().getData();
            handler.characters(s.toCharArray(), 0, s.length());
        } else if (event.isNamespace()) {
            Namespace n = (Namespace) event;
            handler.startPrefixMapping(n.getPrefix(), n.getNamespaceURI());
        } else if (event.isEntityReference()) {
            // ignore
        } else if (event.isProcessingInstruction()) {
            ProcessingInstruction e = (ProcessingInstruction) event;
            handler.processingInstruction(e.getTarget(), e.getData());
        } else if (event.isStartDocument()) {
            handler.startDocument();
        }
    }

    private static String qname(QName name) {
        if (name.getPrefix() != null && !name.getPrefix().isEmpty()) {
            return name.getPrefix() + ":" + name.getLocalPart();
        } else {
            return name.getLocalPart();
        }
    }
    
    private static Consumer<XMLEvent> addTo(XMLEventWriter writer) {
        return evt -> {
            try {
                writer.add(evt);
            } catch (XMLStreamException e) {
                throw new IllegalArgumentException(e);
            }
        };
    }
    
    @SuppressWarnings("unchecked")
    private static <T> T instantiate(String className) {
        try {
            return (T) Class.forName(className).newInstance();
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}

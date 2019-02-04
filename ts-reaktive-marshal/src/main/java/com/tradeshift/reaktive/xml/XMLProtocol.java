package com.tradeshift.reaktive.xml;

import javax.xml.bind.JAXBContext;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.Namespace;
import javax.xml.stream.events.XMLEvent;

import com.tradeshift.reaktive.marshal.Locator;
import com.tradeshift.reaktive.marshal.Protocol;
import com.tradeshift.reaktive.marshal.ReadProtocol;
import com.tradeshift.reaktive.marshal.WriteProtocol;

import io.vavr.Function1;
import io.vavr.Function2;
import io.vavr.Function3;
import io.vavr.Function4;
import io.vavr.Function5;
import io.vavr.Function6;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashSet;
import io.vavr.collection.Vector;
import io.vavr.control.Option;

@SuppressWarnings("unchecked")
public class XMLProtocol {
    private static final XMLEventFactory factory = XMLEventFactory.newFactory();
    public static final Locator<XMLEvent> locator = evt -> evt.getLocation().getLineNumber() + ":" + evt.getLocation().getColumnNumber();

    //---------------------- 0-arity tag methods -----------------------------------
    
    /**
     * Matches an exact XML tag, and emits the start and end tags themselves, including any sub-events that make up its body.
     */
    public static Protocol<XMLEvent, XMLEvent> tag(QName name) {
        return new SelectedTagProtocol(name);
    }
    
    /**
     * Accepts any single tag when reading, routing its body through to the given inner protocol.
     */
    public static <T> ReadProtocol<XMLEvent, T> anyTag(ReadProtocol<XMLEvent, T> inner) {
        return new TagReadProtocol<>(Option.none(), inner);
    }

    /**
     * Matches a tag or (grant)child tag with the given name(s), emitting all tha events that make up that tag and its content.
     */
    public static SelectedSubTagProtocol anySubTag(QName... names) {
        return new SelectedSubTagProtocol(HashSet.of(names));
    }

    /**
     * Matches a tag or (grant)child tag with the given name(s), emitting all tha events that make up that tag and its content.
     */
    public static SelectedSubTagProtocol anySubTag(Iterable<QName> names) {
        return new SelectedSubTagProtocol(HashSet.ofAll(names));
    }

    //---------------------- 1-arity tag methods -----------------------------------
    
    /**
     * Reads and writes a tag and one child element (tag or attribute), where the result of this tag is the result of the single child.
     */
    public static <T> TagProtocol<T> tag(QName name, Protocol<XMLEvent,T> p1) {
        return new TagProtocol<>(Option.of(name), p1);
    }
    
    /**
     * Reads a tag and one child element (tag or attribute), where the result of this tag is the result of the single child.
     */
    public static <T> TagReadProtocol<T> tag(QName name, ReadProtocol<XMLEvent,T> p1) {
        return new TagReadProtocol<>(Option.of(name), p1);
    }
    
    /**
     * Writes a tag and one child element (tag or attribute), where the result of this tag is the result of the single child.
     */
    public static <T> TagWriteProtocol<T> tag(QName name, WriteProtocol<XMLEvent,T> p1) {
        return new TagWriteProtocol<>(Option.of(name), Vector.of(p1), Vector.of(Function1.identity()));
    }
    
    /**
     * Reads and writes a tag and one child element (tag or attribute) using [p1], using [f] to create the result on reading, getting values using [g1] for writing.
     */
    public static <F1,T> TagProtocol<T> tag(QName qname, Protocol<XMLEvent,F1> p1, Function1<F1,T> f, Function1<T,F1> g1) {
        return new TagProtocol<>(Option.of(qname), Vector.of(p1), args -> f.apply((F1) args.get(0)), Vector.of(g1));
    }
    
    /**
     * Reads a tag and one child element (tag or attribute) using [p1], creating its result using [f].
     */
    public static <F1,T> TagReadProtocol<T> tag(QName name, ReadProtocol<XMLEvent,F1> p1, Function1<F1,T> f) {
        return new TagReadProtocol<>(Option.of(name), Vector.of(p1), args -> f.apply((F1) args.get(0)));
    }
    
    /**
     * Writes a tag and one child element (tag or attribute) using [p1], getting values using [g1] for writing.
     */
    public static <F1,T> TagWriteProtocol<T> tag(QName qname, Function1<T,F1> g1, WriteProtocol<XMLEvent,F1> p1) {
        return new TagWriteProtocol<>(Option.of(qname), Vector.of(p1), Vector.of(g1));
    }
    
    //---------------------- 2-arity tag methods -----------------------------------
    
    /**
     * Reads and writes a tag and child elements (tag or attribute) using [p*], using [f] to create the result on reading, getting values using [g*] for writing.
     */
    public static <F1,F2,T> TagProtocol<T> tag(QName qname, Protocol<XMLEvent,F1> p1, Protocol<XMLEvent,F2> p2, Function2<F1,F2,T> f, Function1<T,F1> g1, Function1<T,F2> g2) {
        return new TagProtocol<>(Option.of(qname), Vector.of(p1, p2), args -> f.apply((F1) args.get(0), (F2) args.get(1)), Vector.of(g1, g2));
    }
    
    /**
     * Reads a tag and child elements (tag or attribute) using [p*], using [f] to create the result on reading.
     */
    public static <F1,F2,T> TagReadProtocol<T> tag(QName qname, ReadProtocol<XMLEvent,F1> p1, ReadProtocol<XMLEvent,F2> p2, Function2<F1,F2,T> f) {
        return new TagReadProtocol<>(Option.of(qname), Vector.of(p1, p2), args -> f.apply((F1) args.get(0), (F2) args.get(1)));
    }
    
    /**
     * Writes a tag and child elements (tag or attribute) using [p*], getting values using [g*] for writing.
     */
    public static <F1,F2,T> TagWriteProtocol<T> tag(QName qname, Function1<T,F1> g1, WriteProtocol<XMLEvent,F1> p1, Function1<T,F2> g2, WriteProtocol<XMLEvent,F2> p2) {
        return new TagWriteProtocol<>(Option.of(qname), Vector.of(p1, p2), Vector.of(g1, g2));
    }
    
    /**
     * Reads and writes a tag and child elements (tag or attribute) using [p*], represented by a Tuple2.
     */
    public static <F1,F2> TagProtocol<Tuple2<F1,F2>> tag(QName qname, Protocol<XMLEvent,F1> p1, Protocol<XMLEvent,F2> p2) {
        return tag(qname, p1, p2, Tuple::of, Tuple2::_1, Tuple2::_2);
    }
    
    /**
     * Reads a tag and child elements (tag or attribute) using [p*], represented by a Tuple2.
     */
    public static <F1,F2> TagReadProtocol<Tuple2<F1,F2>> tag(QName qname, ReadProtocol<XMLEvent,F1> p1, ReadProtocol<XMLEvent,F2> p2) {
        return tag(qname, p1, p2, Tuple::of);
    }
    
    /**
     * Writes a tag and child elements (tag or attribute) using [p*], represented by a Tuple2.
     */
    public static <F1,F2> TagWriteProtocol<Tuple2<F1,F2>> tag(QName qname, WriteProtocol<XMLEvent,F1> p1, WriteProtocol<XMLEvent,F2> p2) {
        return tag(qname, Tuple2::_1, p1, Tuple2::_2, p2);
    }
    
    //---------------------- 3-arity tag methods -----------------------------------
    
    /**
     * Reads and writes a tag and child elements (tag or attribute) using [p*], using [f] to create the result on reading, getting values using [g*] for writing.
     */
    public static <F1,F2,F3,T> TagProtocol<T> tag(QName qname, Protocol<XMLEvent,F1> p1, Protocol<XMLEvent,F2> p2, Protocol<XMLEvent,F3> p3, Function3<F1,F2,F3,T> f, Function1<T,F1> g1, Function1<T,F2> g2, Function1<T,F3> g3) {
        return new TagProtocol<>(Option.of(qname), Vector.of(p1, p2, p3), args -> f.apply((F1) args.get(0), (F2) args.get(1), (F3) args.get(2)), Vector.of(g1, g2, g3));
    }
    /**
     * Reads a tag and child elements (tag or attribute) using [p*], using [f] to create the result on reading.
     */
    public static <F1,F2,F3,T> TagReadProtocol<T> tag(QName qname, ReadProtocol<XMLEvent,F1> p1, ReadProtocol<XMLEvent,F2> p2, ReadProtocol<XMLEvent,F3> p3, Function3<F1,F2,F3,T> f) {
        return new TagReadProtocol<>(Option.of(qname), Vector.of(p1, p2, p3), args -> f.apply((F1) args.get(0), (F2) args.get(1), (F3) args.get(2)));
    }
    /**
     * Writes a tag and child elements (tag or attribute) using [p*], getting values using [g*] for writing.
     */
    public static <F1,F2,F3,T> TagWriteProtocol<T> tag(QName qname, Function1<T,F1> g1, WriteProtocol<XMLEvent,F1> p1, Function1<T,F2> g2, WriteProtocol<XMLEvent,F2> p2, Function1<T,F3> g3, WriteProtocol<XMLEvent,F3> p3) {
        return new TagWriteProtocol<>(Option.of(qname), Vector.of(p1, p2, p3), Vector.of(g1, g2, g3));
    }
    
    //---------------------- 4-arity tag methods -----------------------------------
    
    /**
     * Reads and writes a tag and child elements (tag or attribute) using [p*], using [f] to create the result on reading, getting values using [g*] for writing.
     */
    public static <F1,F2,F3,F4,T> TagProtocol<T> tag(QName qname, Protocol<XMLEvent,F1> p1, Protocol<XMLEvent,F2> p2, Protocol<XMLEvent,F3> p3, Protocol<XMLEvent,F4> p4, Function4<F1,F2,F3,F4,T> f, Function1<T,F1> g1, Function1<T,F2> g2, Function1<T,F3> g3, Function1<T,F4> g4) {
        return new TagProtocol<>(Option.of(qname), Vector.of(p1, p2, p3, p4), args -> f.apply((F1) args.get(0), (F2) args.get(1), (F3) args.get(2), (F4) args.get(3)), Vector.of(g1, g2, g3, g4));
    }
    /**
     * Reads a tag and child elements (tag or attribute) using [p*], using [f] to create the result on reading.
     */
    public static <F1,F2,F3,F4,T> TagReadProtocol<T> tag(QName qname, ReadProtocol<XMLEvent,F1> p1, ReadProtocol<XMLEvent,F2> p2, ReadProtocol<XMLEvent,F3> p3, ReadProtocol<XMLEvent,F4> p4, Function4<F1,F2,F3,F4,T> f) {
        return new TagReadProtocol<>(Option.of(qname), Vector.of(p1, p2, p3, p4), args -> f.apply((F1) args.get(0), (F2) args.get(1), (F3) args.get(2), (F4) args.get(3)));
    }
    /**
     * Writes a tag and child elements (tag or attribute) using [p*], getting values using [g*] for writing.
     */
    public static <F1,F2,F3,F4,T> TagWriteProtocol<T> tag(QName qname, Function1<T,F1> g1, WriteProtocol<XMLEvent,F1> p1, Function1<T,F2> g2, WriteProtocol<XMLEvent,F2> p2, Function1<T,F3> g3, WriteProtocol<XMLEvent,F3> p3, Function1<T,F4> g4, WriteProtocol<XMLEvent,F4> p4) {
        return new TagWriteProtocol<>(Option.of(qname), Vector.of(p1, p2, p3, p4), Vector.of(g1, g2, g3, g4));
    }

    //---------------------- 5-arity tag methods -----------------------------------
    
    /**
     * Reads and writes a tag and child elements (tag or attribute) using [p*], using [f] to create the result on reading, getting values using [g*] for writing.
     */
    public static <F1,F2,F3,F4,F5,T> TagProtocol<T> tag(QName qname, Protocol<XMLEvent,F1> p1, Protocol<XMLEvent,F2> p2, Protocol<XMLEvent,F3> p3, Protocol<XMLEvent,F4> p4, Protocol<XMLEvent,F5> p5, Function5<F1,F2,F3,F4,F5,T> f, Function1<T,F1> g1, Function1<T,F2> g2, Function1<T,F3> g3, Function1<T,F4> g4, Function1<T,F5> g5) {
        return new TagProtocol<>(Option.of(qname), Vector.of(p1, p2, p3, p4, p5), args -> f.apply((F1) args.get(0), (F2) args.get(1), (F3) args.get(2), (F4) args.get(3), (F5) args.get(4)), Vector.of(g1, g2, g3, g4, g5));
    }
    /**
     * Reads a tag and child elements (tag or attribute) using [p*], using [f] to create the result on reading.
     */
    public static <F1,F2,F3,F4,F5,T> TagReadProtocol<T> tag(QName qname, ReadProtocol<XMLEvent,F1> p1, ReadProtocol<XMLEvent,F2> p2, ReadProtocol<XMLEvent,F3> p3, ReadProtocol<XMLEvent,F4> p4, ReadProtocol<XMLEvent,F5> p5, Function5<F1,F2,F3,F4,F5,T> f) {
        return new TagReadProtocol<>(Option.of(qname), Vector.of(p1, p2, p3, p4, p5), args -> f.apply((F1) args.get(0), (F2) args.get(1), (F3) args.get(2), (F4) args.get(3), (F5) args.get(4)));
    }
    /**
     * Writes a tag and child elements (tag or attribute) using [p*], getting values using [g*] for writing.
     */
    public static <F1,F2,F3,F4,F5,T> TagWriteProtocol<T> tag(QName qname, Function1<T,F1> g1, WriteProtocol<XMLEvent,F1> p1, Function1<T,F2> g2, WriteProtocol<XMLEvent,F2> p2, Function1<T,F3> g3, WriteProtocol<XMLEvent,F3> p3, Function1<T,F4> g4, WriteProtocol<XMLEvent,F4> p4, Function1<T,F5> g5, WriteProtocol<XMLEvent,F5> p5) {
        return new TagWriteProtocol<>(Option.of(qname), Vector.of(p1, p2, p3, p4, p5), Vector.of(g1, g2, g3, g4, g5));
    }

    //---------------------- 6-arity tag methods -----------------------------------
    
    /**
     * Reads and writes a tag and child elements (tag or attribute) using [p*], using [f] to create the result on reading, getting values using [g*] for writing.
     */
    public static <F1,F2,F3,F4,F5,F6,T> TagProtocol<T> tag(QName qname, Protocol<XMLEvent,F1> p1, Protocol<XMLEvent,F2> p2, Protocol<XMLEvent,F3> p3, Protocol<XMLEvent,F4> p4, Protocol<XMLEvent,F5> p5, Protocol<XMLEvent,F6> p6, Function6<F1,F2,F3,F4,F5,F6,T> f, Function1<T,F1> g1, Function1<T,F2> g2, Function1<T,F3> g3, Function1<T,F4> g4, Function1<T,F5> g5, Function1<T,F6> g6) {
        return new TagProtocol<>(Option.of(qname), Vector.of(p1, p2, p3, p4, p5, p6), args -> f.apply((F1) args.get(0), (F2) args.get(1), (F3) args.get(2), (F4) args.get(3), (F5) args.get(4), (F6) args.get(5)), Vector.of(g1, g2, g3, g4, g5, g6));
    }
    /**
     * Reads a tag and child elements (tag or attribute) using [p*], using [f] to create the result on reading.
     */
    public static <F1,F2,F3,F4,F5,F6,T> TagReadProtocol<T> tag(QName qname, ReadProtocol<XMLEvent,F1> p1, ReadProtocol<XMLEvent,F2> p2, ReadProtocol<XMLEvent,F3> p3, ReadProtocol<XMLEvent,F4> p4, ReadProtocol<XMLEvent,F5> p5, ReadProtocol<XMLEvent,F6> p6, Function6<F1,F2,F3,F4,F5,F6,T> f) {
        return new TagReadProtocol<>(Option.of(qname), Vector.of(p1, p2, p3, p4, p5, p6), args -> f.apply((F1) args.get(0), (F2) args.get(1), (F3) args.get(2), (F4) args.get(3), (F5) args.get(4), (F6) args.get(5)));
    }
    /**
     * Writes a tag and child elements (tag or attribute) using [p*], getting values using [g*] for writing.
     */
    public static <F1,F2,F3,F4,F5,F6,T> TagWriteProtocol<T> tag(QName qname, Function1<T,F1> g1, WriteProtocol<XMLEvent,F1> p1, Function1<T,F2> g2, WriteProtocol<XMLEvent,F2> p2, Function1<T,F3> g3, WriteProtocol<XMLEvent,F3> p3, Function1<T,F4> g4, WriteProtocol<XMLEvent,F4> p4,   Function1<T,F5> g5, WriteProtocol<XMLEvent,F5> p5, Function1<T,F6> g6, WriteProtocol<XMLEvent,F6> p6) {
        return new TagWriteProtocol<>(Option.of(qname), Vector.of(p1, p2, p3, p4, p5, p6), Vector.of(g1, g2, g3, g4, g5, g6));
    }
    
    //---------------------- 1-arity tagName methods -----------------------------------
    
    /**
     * Reads and writes a tag with any name
     */
    public static final Protocol<XMLEvent,QName> tagName = tagName(Function1.identity(), Function1.identity());
    
    /**
     * Reads and writes a tag with any name, using [f] to create the result on reading, getting values using [g1] for writing.
     */
    public static <T> TagProtocol<T> tagName(Function1<QName,T> f, Function1<T,QName> g1) {
        return new TagProtocol<>(Option.none(), Vector.empty(), args -> f.apply((QName) args.get(0)), Vector.of(g1));
    }
    
    /**
     * Reads a tag with any name, creating its result using [f].
     */
    public static <T> TagReadProtocol<T> readTagName(Function1<QName,T> f) {
        return new TagReadProtocol<>(Option.none(), Vector.empty(), args -> f.apply((QName) args.get(0)));
    }
    
    /**
     * Writes a tag and with any name, getting values using [g1] for writing.
     */
    public static <T> TagWriteProtocol<T> writeTagName(Function1<T,QName> g1) {
        return new TagWriteProtocol<>(Option.none(), Vector.empty(), Vector.of(g1));
    }
    
    //---------------------- 2-arity tagName methods -----------------------------------
    
    /**
     * Reads and writes a tag with any name and inner protocols using [p*], using [f] to create the result on reading, getting values using [g*] for writing.
     */
    public static <F2,T> TagProtocol<T> tagName(Protocol<XMLEvent,F2> p2, Function2<QName,F2,T> f, Function1<T,QName> g1, Function1<T,F2> g2) {
        return new TagProtocol<>(Option.none(), Vector.of(p2), args -> f.apply((QName) args.get(0), (F2) args.get(1)), Vector.of(g1, g2));
    }
    
    /**
     * Reads a tag with any name and inner protocols using [p*], using [f] to create the result on reading.
     */
    public static <F2,T> TagReadProtocol<T> tagName(ReadProtocol<XMLEvent,F2> p2, Function2<QName,F2,T> f) {
        return new TagReadProtocol<>(Option.none(), Vector.of(p2), args -> f.apply((QName) args.get(0), (F2) args.get(1)));
    }
    
    /**
     * Writes a tag with any name and inner protocols using [p*], getting values using [g*] for writing.
     */
    public static <F2,T> TagWriteProtocol<T> tagName(Function1<T,QName> g1, Function1<T,F2> g2, WriteProtocol<XMLEvent,F2> p2) {
        return new TagWriteProtocol<>(Option.none(), Vector.of(p2), Vector.of(g1, g2));
    }
    
    /**
     * Reads and writes a tag with any name and inner protocols using [p*], represented by a Tuple2.
     */
    public static <F2> TagProtocol<Tuple2<QName,F2>> tagNameAnd(Protocol<XMLEvent,F2> p2) {
        return tagName(p2, Tuple::of, Tuple2::_1, Tuple2::_2);
    }
    
    /**
     * Reads a tag with any name and inner protocols using [p*], represented by a Tuple2.
     */
    public static <F2> TagReadProtocol<Tuple2<QName,F2>> tagNameAnd(ReadProtocol<XMLEvent,F2> p2) {
        return tagName(p2, Tuple::of);
    }
    
    /**
     * Writes a tag with any name and inner protocols using [p*], represented by a Tuple2.
     */
    public static <F2> TagWriteProtocol<Tuple2<QName,F2>> tagNameAnd(WriteProtocol<XMLEvent,F2> p2) {
        return tagName(Tuple2::_1, Tuple2::_2, p2);
    }
    
    //---------------------- 3-arity tagName methods -----------------------------------
    
    /**
     * Reads and writes a tag with any name and inner protocols using [p*], using [f] to create the result on reading, getting values using [g*] for writing.
     */
    public static <F2,F3,T> TagProtocol<T> tagName(Protocol<XMLEvent,F2> p2, Protocol<XMLEvent,F3> p3, Function3<QName,F2,F3,T> f, Function1<T,QName> g1, Function1<T,F2> g2, Function1<T,F3> g3) {
        return new TagProtocol<>(Option.none(), Vector.of(p2, p3), args -> f.apply((QName) args.get(0), (F2) args.get(1), (F3) args.get(2)), Vector.of(g1, g2, g3));
    }
    /**
     * Reads a tag with any name and inner protocols using [p*], using [f] to create the result on reading.
     */
    public static <F2,F3,T> TagReadProtocol<T> tagName(ReadProtocol<XMLEvent,F2> p2, ReadProtocol<XMLEvent,F3> p3, Function3<QName,F2,F3,T> f) {
        return new TagReadProtocol<>(Option.none(), Vector.of(p2, p3), args -> f.apply((QName) args.get(0), (F2) args.get(1), (F3) args.get(2)));
    }
    /**
     * Writes a tag with any name and inner protocols using [p*], getting values using [g*] for writing.
     */
    public static <F2,F3,T> TagWriteProtocol<T> tagName(Function1<T,QName> g1, Function1<T,F2> g2, WriteProtocol<XMLEvent,F2> p2, Function1<T,F3> g3, WriteProtocol<XMLEvent,F3> p3) {
        return new TagWriteProtocol<>(Option.none(), Vector.of(p2, p3), Vector.of(g1, g2, g3));
    }
    
    // --------------------------------------------------------------------------------
    
    /**
     * Reads and writes a namespaced string attribute
     */
    public static AttributeProtocol attribute(Namespace ns, String name) {
        return attribute(qname(ns, name));
    }
    
    /**
     * Reads and writes a string attribute in the default namespace
     */
    public static AttributeProtocol attribute(String name) {
        return attribute(qname(name));
    }
    
    /**
     * Reads and writes a namespaced string attribute
     */
    public static AttributeProtocol attribute(QName name) {
        return new AttributeProtocol(name);
    }
    
    /**
     * Reads and writes the body of the current XML tag
     */
    public static final BodyProtocol body = BodyProtocol.INSTANCE;
    
    public static final Protocol<XMLEvent, Characters> bodyEvents = BodyEventsProtocol.INSTANCE;
    
    /**
     * Returns a QName for a tag in the default namespace.
     */
    public static QName qname(String name) {
        return new QName(name);
    }
    
    /**
     * Combines a Namespace and local name into a QName.
     */
    public static QName qname(Namespace ns, String name) {
        return new QName(ns.getNamespaceURI(), name, ns.getPrefix());
    }

    /**
     * Creates a Namespace that can be used both for reading and writing XMl.
     */
    public static final Namespace ns(String prefix, String namespace) {
        return factory.createNamespace(prefix, namespace);
    }
    
    /**
     * Creates a Namespace that can only be used for reading XML.
     */
    public static final Namespace ns(String namespace) {
        return factory.createNamespace(namespace);
    }
    
    /**
     * Reads and writes every top-level tag's QName and its body as a Tuple2.
     */
    public static final QNameStringProtocol anyTagWithBody = new QNameStringProtocol(tagNameAnd(body));
    
    /**
     * Reads and writes every top-level tag's QName and the (required) attribute [name] as a Tuple2.
     */
    public static QNameStringProtocol anyTagWithAttribute(String name) {
        return new QNameStringProtocol(tagNameAnd(attribute(name)));
    }
    
    /**
     * Reads and writes every top-level attribute's QName and its value as a Tuple2.
     */
    public static final QNameStringProtocol anyAttribute = new QNameStringProtocol(AnyAttributeProtocol.INSTANCE);
    
    /**
     * Reads and writes a JAXB-annotated class of a known type, by creating a default JAXBContext containing just that type.
     */
    public static <T> Protocol<XMLEvent,T> jaxbType(Class<T> targetType) {
        return JAXBProtocol.jaxbType(targetType);
    }
    
    /**
     * Reads and writes a JAXB-annotated class of a known type, by using the given JAXB context.
     */
    public static <T> Protocol<XMLEvent,T> jaxbType(Class<T> targetType, JAXBContext context) {
        return JAXBProtocol.jaxbType(targetType, context);
    }
    
    /**
     * Reads and writes a JAXB-annotated class of an arbitrary type, by using the given JAXB context.
     * Since this method is not type-safe, it's up to the user to ensure that the given JAXB context
     * can handle instances and XML given to the protocol.
     */
    public static Protocol<XMLEvent,Object> jaxbType(JAXBContext context) {
        return JAXBProtocol.jaxbType(context);
    }
}

package com.tradeshift.reaktive.xml;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Consumer;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.events.Namespace;

import com.tradeshift.reaktive.marshal.StringMarshallable;

import javaslang.Function1;
import javaslang.Function2;
import javaslang.Function3;
import javaslang.Tuple;
import javaslang.Tuple2;
import javaslang.collection.HashMap;
import javaslang.collection.Map;
import javaslang.collection.Seq;
import javaslang.collection.Vector;
import javaslang.control.Option;

@SuppressWarnings("unchecked")
public abstract class XMLProtocol<T> extends XMLReadProtocol<T> implements XMLWriteProtocol<T> {
    private static final XMLEventFactory factory = XMLEventFactory.newFactory();

    //---------------------- 1-arity tag methods -----------------------------------
    
    /**
     * Reads and writes a tag and one child element (tag or attribute), where the result of this tag is the result of the single child.
     */
    public static <T> TagProtocol<T> tag(QName name, XMLProtocol<T> p1) {
        return tag(name, p1, Function1.identity(), Function1.identity());
    }
    
    /**
     * Reads a tag and one child element (tag or attribute), where the result of this tag is the result of the single child.
     */
    public static <T> TagReadProtocol<T> tag(QName name, XMLReadProtocol<T> p1) {
        return tag(name, p1, Function1.identity());
    }
    
    /**
     * Writes a tag and one child element (tag or attribute), where the result of this tag is the result of the single child.
     */
    public static <T> TagWriteProtocol<T> tag(QName name, XMLWriteProtocol<T> p1) {
        return tag(name, Function1.identity(), p1);
    }
    
    /**
     * Reads and writes a tag and one child element (tag or attribute) using [p1], using [f] to create the result on reading, getting values using [g1] for writing.
     */
    public static <F1,T> TagProtocol<T> tag(QName qname, XMLProtocol<F1> p1, Function1<F1,T> f, Function1<T,F1> g1) {
        return new TagProtocol<>(Option.of(qname), Vector.of(p1), args -> f.apply((F1) args.get(0)), Vector.of(g1));
    }
    
    /**
     * Reads a tag and one child element (tag or attribute) using [p1], creating its result using [f].
     */
    public static <F1,T> TagReadProtocol<T> tag(QName name, XMLReadProtocol<F1> p1, Function1<F1,T> f) {
        return new TagReadProtocol<>(Option.of(name), Vector.of(p1), args -> f.apply((F1) args.get(0)));
    }
    
    /**
     * Writes a tag and one child element (tag or attribute) using [p1], getting values using [g1] for writing.
     */
    public static <F1,T> TagWriteProtocol<T> tag(QName qname, Function1<T,F1> g1, XMLWriteProtocol<F1> p1) {
        return new TagWriteProtocol<>(Option.of(qname), Vector.of(p1), Vector.of(g1));
    }
    
    //---------------------- 2-arity tag methods -----------------------------------
    
    /**
     * Reads and writes a tag and child elements (tag or attribute) using [p*], using [f] to create the result on reading, getting values using [g*] for writing.
     */
    public static <F1,F2,T> TagProtocol<T> tag(QName qname, XMLProtocol<F1> p1, XMLProtocol<F2> p2, Function2<F1,F2,T> f, Function1<T,F1> g1, Function1<T,F2> g2) {
        return new TagProtocol<>(Option.of(qname), Vector.of(p1, p2), args -> f.apply((F1) args.get(0), (F2) args.get(1)), Vector.of(g1, g2));
    }
    
    /**
     * Reads a tag and child elements (tag or attribute) using [p*], using [f] to create the result on reading.
     */
    public static <F1,F2,T> TagReadProtocol<T> tag(QName qname, XMLReadProtocol<F1> p1, XMLReadProtocol<F2> p2, Function2<F1,F2,T> f) {
        return new TagReadProtocol<>(Option.of(qname), Vector.of(p1, p2), args -> f.apply((F1) args.get(0), (F2) args.get(1)));
    }
    
    /**
     * Writes a tag and child elements (tag or attribute) using [p*], getting values using [g*] for writing.
     */
    public static <F1,F2,T> TagWriteProtocol<T> tag(QName qname, Function1<T,F1> g1, XMLWriteProtocol<F1> p1, Function1<T,F2> g2, XMLWriteProtocol<F2> p2) {
        return new TagWriteProtocol<>(Option.of(qname), Vector.of(p1, p2), Vector.of(g1, g2));
    }
    
    /**
     * Reads and writes a tag and child elements (tag or attribute) using [p*], represented by a Tuple2.
     */
    public static <F1,F2> TagProtocol<Tuple2<F1,F2>> tag(QName qname, XMLProtocol<F1> p1, XMLProtocol<F2> p2) {
        return tag(qname, p1, p2, Tuple::of, Tuple2::_1, Tuple2::_2);
    }
    
    /**
     * Reads a tag and child elements (tag or attribute) using [p*], represented by a Tuple2.
     */
    public static <F1,F2> TagReadProtocol<Tuple2<F1,F2>> tag(QName qname, XMLReadProtocol<F1> p1, XMLReadProtocol<F2> p2) {
        return tag(qname, p1, p2, Tuple::of);
    }
    
    /**
     * Writes a tag and child elements (tag or attribute) using [p*], represented by a Tuple2.
     */
    public static <F1,F2> TagWriteProtocol<Tuple2<F1,F2>> tag(QName qname, XMLWriteProtocol<F1> p1, XMLWriteProtocol<F2> p2) {
        return tag(qname, Tuple2::_1, p1, Tuple2::_2, p2);
    }
    
    //---------------------- 3-arity tag methods -----------------------------------
    
    /**
     * Reads and writes a tag and child elements (tag or attribute) using [p*], using [f] to create the result on reading, getting values using [g*] for writing.
     */
    public static <F1,F2,F3,T> TagProtocol<T> tag(QName qname, XMLProtocol<F1> p1, XMLProtocol<F2> p2, XMLProtocol<F3> p3, Function3<F1,F2,F3,T> f, Function1<T,F1> g1, Function1<T,F2> g2, Function1<T,F3> g3) {
        return new TagProtocol<>(Option.of(qname), Vector.of(p1, p2, p3), args -> f.apply((F1) args.get(0), (F2) args.get(1), (F3) args.get(2)), Vector.of(g1, g2, g3));
    }
    /**
     * Reads a tag and child elements (tag or attribute) using [p*], using [f] to create the result on reading.
     */
    public static <F1,F2,F3,T> TagReadProtocol<T> tag(QName qname, XMLReadProtocol<F1> p1, XMLReadProtocol<F2> p2, XMLReadProtocol<F3> p3, Function3<F1,F2,F3,T> f) {
        return new TagReadProtocol<>(Option.of(qname), Vector.of(p1, p2, p3), args -> f.apply((F1) args.get(0), (F2) args.get(1), (F3) args.get(2)));
    }
    /**
     * Writes a tag and child elements (tag or attribute) using [p*], getting values using [g*] for writing.
     */
    public static <F1,F2,F3,T> TagWriteProtocol<T> tag(QName qname, Function1<T,F1> g1, XMLWriteProtocol<F1> p1, Function1<T,F2> g2, XMLWriteProtocol<F2> p2, Function1<T,F3> g3, XMLWriteProtocol<F3> p3) {
        return new TagWriteProtocol<>(Option.of(qname), Vector.of(p1, p2, p3), Vector.of(g1, g2, g3));
    }
    
    //---------------------- 1-arity anyTag methods -----------------------------------
    
    /**
     * Reads and writes a tag with any name
     */
    public static final XMLProtocol<QName> anyTag = anyTag(Function1.identity(), Function1.identity());
    
    /**
     * Reads and writes a tag with any name, using [f] to create the result on reading, getting values using [g1] for writing.
     */
    public static <T> TagProtocol<T> anyTag(Function1<QName,T> f, Function1<T,QName> g1) {
        return new TagProtocol<>(Option.none(), Vector.empty(), args -> f.apply((QName) args.get(0)), Vector.of(g1));
    }
    
    /**
     * Reads a tag with any name, creating its result using [f].
     */
    public static <T> TagReadProtocol<T> anyTagR(Function1<QName,T> f) {
        return new TagReadProtocol<>(Option.none(), Vector.empty(), args -> f.apply((QName) args.get(0)));
    }
    
    /**
     * Writes a tag and with any name, getting values using [g1] for writing.
     */
    public static <T> TagWriteProtocol<T> anyTagW(Function1<T,QName> g1) {
        return new TagWriteProtocol<>(Option.none(), Vector.empty(), Vector.of(g1));
    }
    
    //---------------------- 2-arity anyTag methods -----------------------------------
    
    /**
     * Reads and writes a tag with any name and inner protocols using [p*], using [f] to create the result on reading, getting values using [g*] for writing.
     */
    public static <F2,T> TagProtocol<T> anyTag(XMLProtocol<F2> p2, Function2<QName,F2,T> f, Function1<T,QName> g1, Function1<T,F2> g2) {
        return new TagProtocol<>(Option.none(), Vector.of(p2), args -> f.apply((QName) args.get(0), (F2) args.get(1)), Vector.of(g1, g2));
    }
    
    /**
     * Reads a tag with any name and inner protocols using [p*], using [f] to create the result on reading.
     */
    public static <F2,T> TagReadProtocol<T> anyTag(XMLReadProtocol<F2> p2, Function2<QName,F2,T> f) {
        return new TagReadProtocol<>(Option.none(), Vector.of(p2), args -> f.apply((QName) args.get(0), (F2) args.get(1)));
    }
    
    /**
     * Writes a tag with any name and inner protocols using [p*], getting values using [g*] for writing.
     */
    public static <F2,T> TagWriteProtocol<T> anyTag(Function1<T,QName> g1, Function1<T,F2> g2, XMLWriteProtocol<F2> p2) {
        return new TagWriteProtocol<>(Option.none(), Vector.of(p2), Vector.of(g1, g2));
    }
    
    /**
     * Reads and writes a tag with any name and inner protocols using [p*], represented by a Tuple2.
     */
    public static <F2> TagProtocol<Tuple2<QName,F2>> anyTag(XMLProtocol<F2> p2) {
        return anyTag(p2, Tuple::of, Tuple2::_1, Tuple2::_2);
    }
    
    /**
     * Reads a tag with any name and inner protocols using [p*], represented by a Tuple2.
     */
    public static <F2> TagReadProtocol<Tuple2<QName,F2>> anyTag(XMLReadProtocol<F2> p2) {
        return anyTag(p2, Tuple::of);
    }
    
    /**
     * Writes a tag with any name and inner protocols using [p*], represented by a Tuple2.
     */
    public static <F2> TagWriteProtocol<Tuple2<QName,F2>> anyTag(XMLWriteProtocol<F2> p2) {
        return anyTag(Tuple2::_1, Tuple2::_2, p2);
    }
    
    //---------------------- 3-arity anyTag methods -----------------------------------
    
    /**
     * Reads and writes a tag with any name and inner protocols using [p*], using [f] to create the result on reading, getting values using [g*] for writing.
     */
    public static <F2,F3,T> TagProtocol<T> anyTag(XMLProtocol<F2> p2, XMLProtocol<F3> p3, Function3<QName,F2,F3,T> f, Function1<T,QName> g1, Function1<T,F2> g2, Function1<T,F3> g3) {
        return new TagProtocol<>(Option.none(), Vector.of(p2, p3), args -> f.apply((QName) args.get(0), (F2) args.get(1), (F3) args.get(2)), Vector.of(g1, g2, g3));
    }
    /**
     * Reads a tag with any name and inner protocols using [p*], using [f] to create the result on reading.
     */
    public static <F2,F3,T> TagReadProtocol<T> anyTag(XMLReadProtocol<F2> p2, XMLReadProtocol<F3> p3, Function3<QName,F2,F3,T> f) {
        return new TagReadProtocol<>(Option.none(), Vector.of(p2, p3), args -> f.apply((QName) args.get(0), (F2) args.get(1), (F3) args.get(2)));
    }
    /**
     * Writes a tag with any name and inner protocols using [p*], getting values using [g*] for writing.
     */
    public static <F2,F3,T> TagWriteProtocol<T> anyTag(Function1<T,QName> g1, Function1<T,F2> g2, XMLWriteProtocol<F2> p2, Function1<T,F3> g3, XMLWriteProtocol<F3> p3) {
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
     * Reads a string attribute in the default namespace
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
    
    /**
     * Converts an XMLProtocol of String to and from a different type. 
     * It's usually more convenient to call .as() on AttributeProtocol or BodyProcotol explicitly, rather than use this method.
     * 
     * @see AttributeProtocol#as(StringMarshallable)
     * @see BodyProtocol#as(StringMarshallable)
     */
    public static <T> XMLProtocol<T> as(StringMarshallable<T> type, XMLProtocol<String> inner) {
        return new StringMarshallableProtocol<>(type, inner);
    }
    
    /**
     * Reads and writes an inner protocol multiple times. On reading, creates a {@link javaslang.collection.Vector} to hold the values. 
     * On writing, any {@link javaslang.collection.Seq} will do.
     */
    public static <T> XMLProtocol<Seq<T>> vector(XMLProtocol<T> inner) {
        return SeqProtocol.readWrite(inner, Vector::of, Vector.empty());
    }

    /**
     * Reads an inner protocol multiple times. On reading, creates a {@link javaslang.collection.Vector} to represent it.
     */
    public static <T> XMLReadProtocol<Vector<T>> vector(XMLReadProtocol<T> inner) {
        return SeqProtocol.read(inner, Vector::of, Vector.empty());
    }

    /**
     * Writes an inner protocol multiple times, represented by a {@link javaslang.collection.Seq}.
     */
    public static <T> XMLWriteProtocol<Seq<T>> seq(XMLWriteProtocol<T> inner) {
        return SeqProtocol.write(inner);
    }

    /**
     * Writes an inner protocol multiple times, represented by a {@link java.util.Iterable}.
     */
    public static <T> XMLWriteProtocol<Iterable<T>> iterable(XMLWriteProtocol<T> inner) {
        return IterableProtocol.write(inner);
    }

    /**
     * Reads an inner protocol multiple times. On reading, creates a {@link java.util.ArrayList} to represent it.
     */
    public static <T> XMLReadProtocol<ArrayList<T>> arrayList(XMLReadProtocol<T> inner) {
        return IterableProtocol.read(inner, new ArrayList<>(), v -> {
            ArrayList<T> l = new ArrayList<>();
            l.add(v);
            return l;
        }, java.util.List::add);
    }
    
    /**
     * Reads and writes an inner protocol multiple times. On reading, creates a {@link java.util.ArrayList} to hold the values. 
     * On writing, any {@link java.util.List} will do.
     */
    public static <T> XMLProtocol<java.util.List<T>> arrayList(XMLProtocol<T> inner) {
        return IterableProtocol.readWrite(inner, new ArrayList<>(), v -> {
            ArrayList<T> l = new ArrayList<>();
            l.add(v);
            return l;
        }, java.util.List::add);
    }
    
    /**
     * Reads and writes an inner protocol of tuples multiple times. On reading, creates a {@link javaslang.collection.HashMap} to hold the result.
     * On writing, any {@link javaslang.collection.Map} will do.
     */
    public static final <K,V> XMLProtocol<Map<K,V>> hashMap(XMLProtocol<Tuple2<K,V>> inner) {
        return MapProtocol.readWrite(inner, HashMap::of, HashMap.empty());
    }
    
    /**
     * Reads an inner protocol of tuples multiple times. On reading, creates a {@link javaslang.collection.HashMap} to hold the result.
     */
    public static final <K,V> XMLReadProtocol<HashMap<K,V>> hashMap(XMLReadProtocol<Tuple2<K,V>> inner) {
        return MapProtocol.read(inner, HashMap::of, HashMap.empty());
    }
    
    /**
     * Writes a map using an inner protocol, by turning it into writing multiple tuples.
     */
    public static final <K,V> XMLWriteProtocol<Map<K,V>> map(XMLProtocol<Tuple2<K,V>> inner) {
        return MapProtocol.write(inner);
    }
    
    /**
     * Reads and writes a nested protocol optionally, representing it by a {@link javaslang.control.Option}.
     */
    public static <T> XMLProtocol<Option<T>> option(XMLProtocol<T> inner) {
        return OptionProtocol.readWrite(inner);
    }
    
    /**
     * Reads a nested protocol optionally, representing it by a {@link javaslang.control.Option}.
     */
    public static <T> XMLReadProtocol<Option<T>> option(XMLReadProtocol<T> inner) {
        return OptionProtocol.read(inner);
    }

    /**
     * Writes a nested protocol optionally, representing it by a {@link javaslang.control.Option}.
     */
    public static <T> XMLWriteProtocol<Option<T>> option(XMLWriteProtocol<T> inner) {
        return OptionProtocol.write(inner);
    }
    
    /**
     * Folds over a repeated nested protocol, merging the results into a single element.
     */
    public static <T,U> XMLReadProtocol<U> fold(XMLReadProtocol<T> inner, Function1<T,U> map, U initial, Function2<U,U,U> combine) {
        return FoldProtocol.read(inner, map, initial, combine);
    }
    
    /**
     * Invokes the given function for every item the inner protocol emits, while emitting a single null as outer value.
     */
    public static <T> XMLReadProtocol<Void> foreach(XMLReadProtocol<T> inner, Consumer<T> consumer) {
        return FoldProtocol.read(inner, t -> { consumer.accept(t); return null; }, null, (v1,v2) -> null);
    }
    
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
    public static final QNameStringProtocol anyTagWithBody = new QNameStringProtocol(anyTag(body));
    
    /**
     * Reads and writes every top-level tag's QName and the (required) attribute [name] as a Tuple2.
     */
    public static QNameStringProtocol anyTagWithAttribute(String name) {
        return new QNameStringProtocol(anyTag(attribute(name)));
    }
    
    /**
     * Reads and writes every top-level attribute's QName and its value as a Tuple2.
     */
    public static final QNameStringProtocol anyAttribute = new QNameStringProtocol(AnyAttributeProtocol.INSTANCE);
    
    
    /**
     * Forwards read events to multiple alternative protocols, emitting whenever any of the alternatives emit. If multiple 
     * alternatives emit for the same event, the first one wins.
     */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <T> XMLReadProtocol<T> alternatively(XMLReadProtocol<T> first, XMLReadProtocol<T> second, XMLReadProtocol<T>... others) {
        return new AlternativesProtocol<>(Vector.of(first, second).appendAll(Arrays.asList(others)));
    }

    /**
     * Forwards read events to multiple alternative protocols, emitting whenever any of the alternatives emit. If multiple 
     * alternatives emit for the same event, the first one wins.
     */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <T> XMLProtocol<T> alternatively(XMLProtocol<T> first, XMLProtocol<T> second, XMLProtocol<T>... others) {
        return AlternativesProtocol.readWrite(Vector.of(first, second).appendAll(Arrays.asList(others)));
    }

    
    public <U> XMLProtocol<U> map(Function1<T,U> onRead, Function1<U,T> beforeWrite) {
        XMLProtocol<T> parent = this;
        return new XMLProtocol<U>() {
            @Override
            public Reader<U> reader() {
                return parent.reader().map(onRead);
            }

            @Override
            public Writer<U> writer() {
                return parent.writer().compose(beforeWrite);
            }
            
            @Override
            public boolean isAttributeProtocol() {
                return parent.isAttributeProtocol();
            }
        };
    };
    
}

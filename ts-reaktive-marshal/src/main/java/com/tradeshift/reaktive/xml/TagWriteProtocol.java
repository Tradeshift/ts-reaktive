package com.tradeshift.reaktive.xml;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.XMLEvent;

import javaslang.Function1;
import javaslang.Tuple2;
import javaslang.collection.Seq;
import javaslang.collection.Vector;
import javaslang.control.Option;

@SuppressWarnings("unchecked")
public class TagWriteProtocol<T> implements XMLWriteProtocol<T> {
    private static final XMLEventFactory factory = XMLEventFactory.newFactory();    
    
    private final Function<T,QName> getName;
    private final Seq<XMLWriteProtocol<?>> attrProtocols;
    private final Seq<XMLWriteProtocol<?>> otherProtocols;
    private final Seq<Function1<T,?>> attrGetters;
    private final Seq<Function1<T,?>> otherGetters;
    private final Writer<T> writer;
    
    /**
     * @param name The qualified name of the tag to write, or none() to have the last item of [getters] deliver a {@link QName}.
     * @param getters Getter function for each sub-protocol to write (and additional first element delivering a QName, if name == none())
     * @param protocols Protocols to use to write each of the getter elements
     */
    public TagWriteProtocol(Option<QName> name, Vector<? extends XMLWriteProtocol<?>> protocols, Vector<Function1<T, ?>> g) {
        if (name.isDefined() && (protocols.size() != g.size()) ||
            name.isEmpty() && (protocols.size() != g.size() - 1)) {
            throw new IllegalArgumentException ("Number of protocols and getters does not match");
        }
        
        this.getName = t -> name.getOrElse(() -> (QName) g.head().apply(t));
        
        Vector<Function1<T, ?>> getters = (name.isEmpty()) ? g.drop(1) : g;
        
        Tuple2<Vector<Tuple2<XMLWriteProtocol<?>, Function1<T, ?>>>, Vector<Tuple2<XMLWriteProtocol<?>, Function1<T, ?>>>> partition = 
            ((Vector<XMLWriteProtocol<?>>)protocols).zip(getters)
            .partition(t -> t._1.isAttributeProtocol());
        
        this.attrProtocols = partition._1().map(t -> t._1());
        this.attrGetters = partition._1().map(t -> t._2());
        this.otherProtocols = partition._2().map(t -> t._1());
        this.otherGetters = partition._2().map(t -> t._2());
        this.writer = createWriter();
    }

    private TagWriteProtocol(Function<T, QName> getName, Seq<XMLWriteProtocol<?>> attrProtocols,
        Seq<XMLWriteProtocol<?>> otherProtocols, Seq<Function1<T, ?>> attrGetters, Seq<Function1<T, ?>> otherGetters) {
        this.getName = getName;
        this.attrProtocols = attrProtocols;
        this.otherProtocols = otherProtocols;
        this.attrGetters = attrGetters;
        this.otherGetters = otherGetters;
        this.writer = createWriter();
    }
    
    public <U> TagWriteProtocol<T> having(XMLWriteProtocol<U> nestedProtocol, U value) {
        return nestedProtocol.isAttributeProtocol() 
            ? new TagWriteProtocol<T>(getName, attrProtocols.append(nestedProtocol), otherProtocols, attrGetters.append(t -> value), otherGetters)
            : new TagWriteProtocol<T>(getName, attrProtocols, otherProtocols.append(nestedProtocol), attrGetters, otherGetters.append(t -> value));
    }

    private Writer<T> createWriter() {
        return value -> Stream.of (
            Stream.of(startElement(value)),
            
            Stream.iterate(0, i -> i + 1).limit(otherProtocols.size()).map(i -> {
                Writer<Object> w = (Writer<Object>) otherProtocols.get(i).writer();
                return w.apply(otherGetters.get(i).apply(value));
            }).flatMap(Function.identity()),            
            
            Stream.of(factory.createEndElement(getName.apply(value), null))
        ).flatMap(Function.identity());
    }

    private XMLEvent startElement(T value) {
        List<Attribute> attributes = new ArrayList<>();
        for (int i = 0; i < attrGetters.size(); i++) {
            Object o = attrGetters.get(i).apply(value);
            XMLWriteProtocol<Object> attributeProtocol = (XMLWriteProtocol<Object>) attrProtocols.get(i);
            attributeProtocol.writer().apply(o).map(Attribute.class::cast).forEach(attributes::add);
        }
        return factory.createStartElement(getName.apply(value), attributes.iterator(), null);
    }

    @Override
    public Writer<T> writer() {
        return writer;
    }
    
    @Override
    public boolean isAttributeProtocol() {
        return false;
    }
}
 
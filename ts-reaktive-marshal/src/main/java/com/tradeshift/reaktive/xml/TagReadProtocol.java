package com.tradeshift.reaktive.xml;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import javax.xml.namespace.QName;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.XMLEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javaslang.collection.Seq;
import javaslang.collection.Vector;
import javaslang.control.Option;
import javaslang.control.Try;

@SuppressWarnings({"unchecked","rawtypes"})
public class TagReadProtocol<T> extends XMLReadProtocol<T> {
    private static final Logger log = LoggerFactory.getLogger(TagReadProtocol.class);
    
    private final Option<QName> name;
    private final Vector<? extends XMLReadProtocol<?>> protocols;
    private final Function<List<?>, T> produce;
    private final Seq<XMLReadProtocol<ConstantProtocol.Present>> conditions;

    /**
     * Creates a new TagReadProtocol
     * @param name Name of the tag to match, or none() to match any tag ([produce] will get another argument (at position 0) with the tag's QName in that case)
     * @param protocols Attributes and child tags to read
     * @param produce Function that must accept a list of (attributes.size + tags.size) objects and turn that into T
     */
    public TagReadProtocol(Option<QName> name, Vector<? extends XMLReadProtocol<?>> protocols, Function<List<?>, T> produce, Seq<XMLReadProtocol<ConstantProtocol.Present>> conditions) {
        this.name = name;
        this.protocols = protocols;
        this.produce = produce;
        this.conditions = conditions;
    }
    
    public TagReadProtocol(Option<QName> name, Vector<? extends XMLReadProtocol<?>> protocols, Function<List<?>, T> produce) {
        this(name, protocols, produce, Vector.empty());
    }
    
    @Override
    public String toString() {
        StringBuilder msg = new StringBuilder("<");
        msg.append(name.map(Object::toString).getOrElse("*"));
        msg.append(">");
        if (!protocols.isEmpty()) {
            msg.append(" with ");
            msg.append(protocols.map(p -> p.toString()).mkString(","));
        }
        return msg.toString();
    }
    
    public <U> TagReadProtocol<T> having(XMLReadProtocol<U> nestedProtocol, U value) {
        return new TagReadProtocol<T>(name, protocols, produce, conditions.append(ConstantProtocol.read(nestedProtocol, value)));
    }
    
    @Override
    public Reader<T> reader() {
        return new Reader<T>() {
            private final Seq<XMLReadProtocol<Object>> all = protocols.map(p -> (XMLReadProtocol<Object>)p).appendAll(conditions.map(p -> XMLReadProtocol.narrow(p))); 
            private final List<Reader<Object>> readers = all.map(p -> p.reader()).toJavaList();
            private final Try<Object>[] values = new Try[readers.size()];
            
            private int level = 0;
            private boolean match = false;
            
            @Override
            public void reset() {
                level = 0;
                match = false;
                readers.forEach(r -> r.reset());
                Arrays.fill(values, null);
            }
            
            @Override
            public Try<T> apply(XMLEvent evt) {
                if (level == 0) {
                    if (evt.isStartElement() && name.filter(n -> !n.equals(evt.asStartElement().getName())).isEmpty()) {
                        level++;
                        match = true;
                        //forward all attributes as attribute events to all sub-readers
                        Iterator i = evt.asStartElement().getAttributes();
                        while (i.hasNext()) {
                            // default JDK implementation doesn't set Location for attributes...
                            Attribute src = (Attribute) i.next();
                            forward(new AttributeDelegate(src, evt.getLocation()));
                        }
                        return none();
                    } else if (evt.isStartElement()) {
                        level++;
                        return none();
                    } else { // character data or other non-tag, just skip
                        return none();
                    }
                } else if (match && level == 1 && evt.isEndElement()) {
                    // Wrap up and emit result
                    
                    AtomicReference<Throwable> failure = new AtomicReference<>();
                    Object[] args = new Object[name.isDefined() ?  values.length : values.length + 1];
                    
                    if (name.isEmpty()) {
                        args[0] = evt.asEndElement().getName();
                    }
                    for (int i = 0; i < all.size(); i++) {
                        XMLReadProtocol<Object> p = all.get(i);
                        Try<Object> t = (values[i] != null) ? values[i] : p.empty();
                        t.failed().forEach(failure::set);
                        args[name.isEmpty() ? i+1 : i] = t.getOrElse((Object)null);
                    }
                    
                    Try<T> result = (failure.get() != null) ? Try.failure(failure.get()) : Try.success(produce.apply(Arrays.asList(args))); 
                    reset();
                    return result;
                } else {
                    if (match) {
                        forward(evt);
                    }
                    
                    if (evt.isStartElement()) {
                        level++;
                    } else if (evt.isEndElement()) {
                        level--;
                    }
                    
                    return none();
                }
            }

            private void forward(XMLEvent evt) {
                for (int i = 0; i < readers.size(); i++) {
                    int idx = i;
                    Reader<Object> r = readers.get(i);
                    log.debug("Sending {} at {} to {}", evt, evt.getLocation().getLineNumber(), r);
                    Try<Object> result = r.apply(evt);
                    result.forEach(value -> { log.debug("   said {}", value); });
                    if (!isNone(result)) {
                        XMLReadProtocol<Object> p = all.get(i);
                        Try<Object> current = (values[idx] != null) ? values[idx] : p.empty();
                        values[idx] = p.combine(current,  result);
                    }
                }
            }
        };
    }
}

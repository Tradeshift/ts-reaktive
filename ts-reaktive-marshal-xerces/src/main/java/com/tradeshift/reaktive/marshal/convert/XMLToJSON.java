package com.tradeshift.reaktive.marshal.convert;

import static javaslang.control.Option.none;
import static javaslang.control.Option.some;

import java.util.Iterator;

import javax.xml.namespace.QName;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.XMLEvent;

import org.apache.xerces.xs.XSComplexTypeDefinition;
import org.apache.xerces.xs.XSElementDeclaration;
import org.apache.xerces.xs.XSModel;
import org.apache.xerces.xs.XSModelGroup;
import org.apache.xerces.xs.XSObjectList;
import org.apache.xerces.xs.XSParticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tradeshift.reaktive.json.JSONEvent;

import akka.stream.Attributes;
import akka.stream.FlowShape;
import akka.stream.Inlet;
import akka.stream.Outlet;
import akka.stream.stage.AbstractInHandler;
import akka.stream.stage.AbstractOutHandler;
import akka.stream.stage.GraphStage;
import akka.stream.stage.GraphStageLogic;
import javaslang.collection.HashMap;
import javaslang.collection.List;
import javaslang.collection.Map;
import javaslang.collection.Seq;
import javaslang.control.Option;

/**
 * Converts XML to JSON, assisted by XSDs, in a similar manner as a JAXB -> Jackson trip would do:
 * - Root tag disappears, but is tagged as an additional root JSON property (configured separately)
 * - Tags that have maxOccurs > 1 go into arrays
 * - Tags that are defined as a complexType (even if just extending xsd:string without attributes) become an object, "TagName" : { "value" : "helloworld" }
 * - Tags that are defined as simple types without attributes become JSON properties
 * - Attributes become JSON properties
 * 
 * Anything outside of the XSD is ignored and not converted to JSON.
 */
public class XMLToJSON extends GraphStage<FlowShape<XMLEvent,JSONEvent>> {
    private static final Logger log = LoggerFactory.getLogger(XMLToJSON.class);

    private static final Inlet<XMLEvent> in = Inlet.create("in");
    private static final Outlet<JSONEvent> out = Outlet.create("out");
    private static final FlowShape<XMLEvent, JSONEvent> shape = FlowShape.of(in, out);
    
    private final XSModel model;
    
    public XMLToJSON(XSModel model) {
        this.model = model;
    }

    @Override
    public FlowShape<XMLEvent, JSONEvent> shape() {
        return shape;
    }

    @Override
    public GraphStageLogic createLogic(Attributes attr) {
        return new GraphStageLogic(shape) {
            /** nested level of ignored tags that weren't found in the XSD */
            private int ignored = 0;
            private Seq<Map<QName,XSParticle>> expected = List.empty();
            private Option<QName> continueArray = none();
            private boolean expectAttributes = false;
            
            {
                setHandler(in, new AbstractInHandler() {
                    @Override
                    public void onPush() throws Exception {
                        XMLEvent evt = grab(in);
                        if (ignored == 0 && expected.isEmpty() && evt.isStartElement()) {
                            handleRootStartTag(evt);
                        } else if (ignored == 0 && evt.isStartElement()) {
                            handleNestedStartTag(evt);
                        } else if (evt.isStartElement()) {
                            ignored++;
                            continueArray = none();
                            pull(in);
                        } else if (ignored > 0 && evt.isEndElement()) {
                            ignored--;
                            pull(in);
                        } else if (evt.isEndElement()) {
                            if (expected.tail().isEmpty()) {
                                handleRootEndTag();
                            } else {
                                handleNestedEndTag(evt);
                            }
                            expectAttributes = false;
                            expected = expected.tail();
                        } else if (ignored == 0 && evt.isCharacters() && evt.asCharacters().getData().trim().length() > 0) {
                            log.debug("chars: {}", evt.asCharacters().getData());
                            if (expectAttributes) {
                                emit(out, new JSONEvent.FieldName("value"));
                            }
                            emit(out, new JSONEvent.StringValue(evt.asCharacters().getData()));
                        } else {
                            pull(in);
                        }
                    }

                    private void handleRootStartTag(XMLEvent evt) {
                        XSElementDeclaration elmt = model.getElementDeclaration(evt.asStartElement().getName().getLocalPart(), evt.asStartElement().getName().getNamespaceURI());
                        if (elmt == null) {
                            ignored++;
                            pull(in);
                            log.warn("Ignoring root tag {}", evt.asStartElement().getName());
                        } else {
                            expected = expected.prepend(getValidSubTags(elmt));
                            log.debug("root: START_OBJECT");
                            emit(out, JSONEvent.START_OBJECT);
                        }
                    }

                    private void handleRootEndTag() {
                        if (continueArray.isDefined()) {
                            log.debug("root: end previous array {}", continueArray.get());
                            emit(out, JSONEvent.END_ARRAY);
                            continueArray = none();
                        }
                        log.debug("root: END_OBJECT");
                        emit(out, JSONEvent.END_OBJECT);
                    }

                    private void handleNestedStartTag(XMLEvent evt) {
                        Option<XSParticle> particle = expected.head().get(new QName(evt.asStartElement().getName().getNamespaceURI(), evt.asStartElement().getName().getLocalPart()));
                        if (particle.isEmpty()) {
                            // Couldn't find the tag in the XSD -> ignore this tag and everything in it
                            ignored++;
                            log.warn("Ignoring nested tag {}", evt.asStartElement().getName());
                            continueArray = none();
                            expectAttributes = false;
                            pull(in);
                        } else {
                            // Valid start tag, was found in the XSD
                            XSElementDeclaration elmt = (XSElementDeclaration) particle.get().getTerm();
                            expected = expected.prepend(getValidSubTags(elmt));
                            log.debug("sub: start field {} while array {}", evt.asStartElement().getName().getLocalPart(), continueArray);
                            if (!continueArray.equals(some(evt.asStartElement().getName()))) {
                                finishArrayIfStarted();
                                emit(out, new JSONEvent.FieldName(evt.asStartElement().getName().getLocalPart()));
                            }
                            expectAttributes = hasAttributes(elmt);
                            if (!expected.head().isEmpty() || expectAttributes) {
                                if (isMultiValued(particle.get())) {
                                    if (continueArray.equals(some(evt.asStartElement().getName()))) {
                                        log.debug("sub: continue array");
                                        emit(out, JSONEvent.START_OBJECT);
                                        continueArray = none();
                                    } else {
                                        log.debug("sub: START_ARRAY");
                                        emit(out, JSONEvent.START_ARRAY);
                                        emit(out, JSONEvent.START_OBJECT);
                                    }
                                } else {
                                    log.debug("sub: START_OBJECT");
                                    emit(out, JSONEvent.START_OBJECT);
                                }
                                if (expectAttributes) {
                                    Iterator<?> i = evt.asStartElement().getAttributes();
                                    while (i.hasNext()) {
                                        Attribute attr = (Attribute) i.next();
                                        emit(out, new JSONEvent.FieldName(attr.getName().getLocalPart()));
                                        emit(out, new JSONEvent.StringValue(attr.getValue()));
                                    }
                                }
                            }
                        }
                    }

                    private void finishArrayIfStarted() {
                        if (continueArray.isDefined()) {
                            log.debug("end previous array");
                            emit(out, JSONEvent.END_ARRAY);
                            continueArray = none();
                        }
                    }
                    
                    private void handleNestedEndTag(XMLEvent evt) {
                        XSParticle particle = expected.tail().head().get(evt.asEndElement().getName()).get();
                        if (!expected.head().isEmpty() || hasAttributes((XSElementDeclaration) particle.getTerm())) {
                            finishArrayIfStarted();
                            if (isMultiValued(particle)) {
                                log.debug("sub: END_OBJECT inside array for {}", evt.asEndElement().getName());
                                emit(out, JSONEvent.END_OBJECT);
                                continueArray = some(evt.asEndElement().getName());
                            } else {
                                log.debug("sub: END_OBJECT for {}", evt.asEndElement().getName());
                                continueArray = none();
                                emit(out, JSONEvent.END_OBJECT);
                            }
                        } else {
                            pull(in);
                        }
                    }
                });
                setHandler(out, new AbstractOutHandler() {
                    @Override
                    public void onPull() {
                        pull(in);
                    }
                });
            }
        };
    }
    
    private Map<QName,XSParticle> getValidSubTags(XSElementDeclaration elmt) {
        if (!(elmt.getTypeDefinition() instanceof XSComplexTypeDefinition)) {
            return HashMap.empty();
        }
        
        XSComplexTypeDefinition type = (XSComplexTypeDefinition) elmt.getTypeDefinition();
        if (type.getParticle() == null || !(type.getParticle().getTerm() instanceof XSModelGroup)) {
            return HashMap.empty();
        }
        
        XSModelGroup group = (XSModelGroup) type.getParticle().getTerm();
        if (group.getCompositor() != XSModelGroup.COMPOSITOR_SEQUENCE && group.getCompositor() != XSModelGroup.COMPOSITOR_CHOICE) {
            return HashMap.empty();
        }
        
        // We don't care whether it's SEQUENCE or CHOICE, we only want to know what are the valid sub-elements at this level.
        XSObjectList particles = group.getParticles();
        Map<QName,XSParticle> content = HashMap.empty();
        for (int j = 0; j < particles.getLength(); j++) {
            XSParticle sub = (XSParticle) particles.get(j);
            if (sub.getTerm() instanceof XSElementDeclaration) {
                XSElementDeclaration term = (XSElementDeclaration) sub.getTerm();
                content = content.put(new QName(term.getNamespace(), term.getName()), sub);
            }
        }
        return content;
    }
    
    private boolean isMultiValued(XSParticle particle) {
        return particle.getMaxOccursUnbounded() || particle.getMaxOccurs() > 1;
    }
    
    private boolean hasAttributes(XSElementDeclaration elmt) {
        if (elmt.getTypeDefinition() instanceof XSComplexTypeDefinition) {
            //Yes, we don't need to wrap this in an object if no attributes can ever occur. But Jackson does, so we must do so too.
            //A future format change could do the following to make the JSON a little nicer:
            //  XSComplexTypeDefinition def = (XSComplexTypeDefinition) elmt.getTypeDefinition();
            //  return def.getAttributeUses().getLength() > 0;
            return true;
        }
        return false;
    }
    
}

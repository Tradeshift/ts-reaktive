package com.tradeshift.reaktive.marshal.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.cuppa.Cuppa.describe;
import static org.forgerock.cuppa.Cuppa.it;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.xmlunit.matchers.CompareMatcher.isIdenticalTo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.XMLEvent;

import org.forgerock.cuppa.junit.CuppaRunner;
import org.junit.runner.RunWith;
import org.xmlunit.builder.Input;

import com.tradeshift.reaktive.testkit.SharedActorSystemSpec;

import akka.stream.javadsl.Source;
import akka.util.ByteString;

@RunWith(CuppaRunner.class)
public class StaxWriterSpec extends SharedActorSystemSpec {{
    describe("StaxWriter", () -> {
        it("Should serialize parsed XML events back to XML", () -> {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            XMLEventReader reader = factory.createXMLEventReader(getClass().getResourceAsStream("/test.xml"));
            List<XMLEvent> events = new ArrayList<>();
            while (reader.hasNext()) {
                events.add(reader.nextEvent());
            }
            
            ByteString result = Source.from(events).via(StaxWriter.flow()).runFold(ByteString.empty(), (s1,s2) -> s1.concat(s2), materializer)
                .toCompletableFuture().get(1, TimeUnit.SECONDS);

            assertThat(result.toArray(), isIdenticalTo(Input.fromURL(getClass().getResource("/test.xml"))));
        });
        
        it("Should augment startElement events with namespace prefix mappings when they're introduced", () -> {
            XMLEventFactory factory = XMLEventFactory.newFactory();
            Attribute attr = factory.createAttribute(new QName("uri:attrns", "a", "atprefix"), "hello");
            ByteString result = Source.from(Arrays.<XMLEvent>asList(
                factory.createStartElement(new QName("uri:ns1","tag","prefix"), null, null),
                factory.createStartElement(new QName("uri:ns2","tag","prefix"), Arrays.asList(attr).iterator(), null),
                factory.createEndElement(new QName("uri:ns2","tag","prefix"), null),
                factory.createStartElement(new QName("uri:ns1","tag","prefix"), null, null),
                factory.createEndElement(new QName("uri:ns1","tag","prefix"), null),
                factory.createEndElement(new QName("uri:ns1","tag","prefix"), null)
            ))
            .via(StaxWriter.flow())
            .runFold(ByteString.empty(), (s1,s2) -> s1.concat(s2), materializer)
            .toCompletableFuture().get(1, TimeUnit.SECONDS);
            
            assertThat(result.utf8String()).isEqualTo(
                "<prefix:tag xmlns:prefix=\"uri:ns1\"><prefix:tag xmlns:prefix=\"uri:ns2\" xmlns:atprefix=\"uri:attrns\" atprefix:a=\"hello\"/><prefix:tag/></prefix:tag>");
        });
        
        it("Should output elements in their respective namespaces when they have no prefix", () -> {
            XMLEventFactory factory = XMLEventFactory.newFactory();
            Attribute attr = factory.createAttribute(new QName("uri:attrns", "a", "atprefix"), "hello");
            ByteString result = Source.from(Arrays.<XMLEvent>asList(
                factory.createStartElement(new QName("uri:ns1","tag",""), null, null),
                factory.createStartElement(new QName("uri:ns2","tag",""), Arrays.asList(attr).iterator(), null),
                factory.createEndElement(new QName("uri:ns2","tag",""), null),
                factory.createEndElement(new QName("uri:ns1","tag",""), null)
            ))
            .via(StaxWriter.flow())
            .runFold(ByteString.empty(), (s1,s2) -> s1.concat(s2), materializer)
            .toCompletableFuture().get(1, TimeUnit.SECONDS);
            
            assertThat(result.toArray(), isIdenticalTo(Input.fromString(
                "<tag xmlns='uri:ns1'><tag xmlns='uri:ns2' xmlns:atprefix='uri:attrns' atprefix:a='hello'/></tag>")));
        });
    });
}}

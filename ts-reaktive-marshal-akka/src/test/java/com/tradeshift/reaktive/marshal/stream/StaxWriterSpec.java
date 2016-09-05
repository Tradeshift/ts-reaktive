package com.tradeshift.reaktive.marshal.stream;

import static org.forgerock.cuppa.Cuppa.describe;
import static org.forgerock.cuppa.Cuppa.it;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.xmlunit.matchers.CompareMatcher.isIdenticalTo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.events.XMLEvent;

import org.forgerock.cuppa.junit.CuppaRunner;
import org.junit.runner.RunWith;
import org.xmlunit.builder.Input;

import com.tradeshift.reaktive.marshal.stream.StaxWriter;
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
    });
}}

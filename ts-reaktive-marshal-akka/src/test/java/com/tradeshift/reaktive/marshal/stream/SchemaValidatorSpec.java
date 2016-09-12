package com.tradeshift.reaktive.marshal.stream;

import static com.tradeshift.reaktive.assertj.CompletionStageAssertions.assertThat;
import static org.forgerock.cuppa.Cuppa.describe;
import static org.forgerock.cuppa.Cuppa.it;

import javax.xml.XMLConstants;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.forgerock.cuppa.junit.CuppaRunner;
import org.junit.runner.RunWith;
import org.xml.sax.SAXException;

import com.tradeshift.reaktive.testkit.SharedActorSystemSpec;

import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.util.ByteString;

@RunWith(CuppaRunner.class)
public class SchemaValidatorSpec extends SharedActorSystemSpec {{
    Schema schema;
    try {
        schema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(getClass().getResource("/test.xsd"));
    } catch (SAXException e) {
        throw new RuntimeException(e);
    }
    
    describe("SchemaValidator", () -> {
        it("should pass through events if they fulfil the schema", () -> {
            assertThat(
                Source.single(ByteString.fromString("<shiporder orderid='123'><orderperson/><shipto><name/><address/><city/><country/></shipto><item><title/><quantity>1</quantity><price>42</price></item></shiporder>"))
                .via(AaltoReader.instance)
                .via(SchemaValidatorFlow.of(schema))
                .runWith(Sink.ignore(), materializer)
            ).succeeds();
        });
        
        it("should fail a stream if it's not valid according to the schema", () -> {
            assertThat(
                Source.single(ByteString.fromString("<shiporder/>"))
                .via(AaltoReader.instance)
                .via(SchemaValidatorFlow.of(schema))
                .runWith(Sink.ignore(), materializer)
            ).failure().hasMessageContaining("'orderid' must appear");
        });
    });
}}

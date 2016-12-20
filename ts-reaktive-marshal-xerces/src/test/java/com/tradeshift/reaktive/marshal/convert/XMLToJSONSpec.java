package com.tradeshift.reaktive.marshal.convert;

import static com.tradeshift.reaktive.assertj.CompletionStageAssertions.assertThat;
import static org.forgerock.cuppa.Cuppa.describe;
import static org.forgerock.cuppa.Cuppa.it;

import org.apache.xerces.impl.xs.XSImplementationImpl;
import org.apache.xerces.xs.XSLoader;
import org.apache.xerces.xs.XSModel;
import org.forgerock.cuppa.junit.CuppaRunner;
import org.junit.runner.RunWith;

import com.tradeshift.reaktive.marshal.stream.AaltoReader;
import com.tradeshift.reaktive.marshal.stream.JacksonWriter;
import com.tradeshift.reaktive.testkit.SharedActorSystemSpec;

import akka.stream.javadsl.StreamConverters;
import akka.util.ByteString;

@RunWith(CuppaRunner.class)
public class XMLToJSONSpec extends SharedActorSystemSpec {
    XSImplementationImpl xs = new XSImplementationImpl();
    XSLoader loader = xs.createXSLoader(null);
    XSModel model = loader.loadURI(getClass().getResource("sample.xsd").toString());
    
    {
        describe("XMLToJSON", () -> {
            it("should convert XML that ends in an array", () -> {
                assertThat(
                    StreamConverters.fromInputStream(() -> getClass().getResourceAsStream("person1.xml"))
                    .via(AaltoReader.instance)
                    .via(new XMLToJSON(model))
                    .via(JacksonWriter.flow())
                    .runFold(ByteString.empty(), (a,b) -> a.concat(b), materializer)
                ).succeedsWith(ByteString.fromString("{"
                    + "\"PersonId\":\"8fb40607-240a-4eb4-b847-db47c9c41751\","
                    + "\"Name\":{\"language\":\"en:US\",\"value\":\"John Smith\"},"
                    + "\"Address\":{\"Street\":\"Main st. 5\",\"City\":\"Bigtown\"},"
                    + "\"Pet\":[{\"Name\":{\"value\":\"Alice\"}},{\"Name\":{\"value\":\"Bob\"}}]"
                    + "}"));
            });
            
            it("should convert XML that has an intermediate array", () -> {
                assertThat(
                    StreamConverters.fromInputStream(() -> getClass().getResourceAsStream("person2.xml"))
                    .via(AaltoReader.instance)
                    .via(new XMLToJSON(model))
                    .via(JacksonWriter.flow())
                    .runFold(ByteString.empty(), (a,b) -> a.concat(b), materializer)
                ).succeedsWith(ByteString.fromString("{"
                    + "\"PersonId\":\"8fb40607-240a-4eb4-b847-db47c9c41751\","
                    + "\"Name\":{\"language\":\"en:US\",\"value\":\"John Smith\"},"
                    + "\"Address\":{\"Street\":\"Main st. 5\",\"City\":\"Bigtown\"},"
                    + "\"Pet\":[{\"Name\":{\"value\":\"Alice\"}},{\"Name\":{\"value\":\"Bob\"}}],"
                    + "\"Favourite\":{\"value\":\"Bob\"}"
                    + "}"));
            });
            
            it("should convert XML that has an array inside another tag", () -> {
                assertThat(
                    StreamConverters.fromInputStream(() -> getClass().getResourceAsStream("person3.xml"))
                    .via(AaltoReader.instance)
                    .via(new XMLToJSON(model))
                    .via(JacksonWriter.flow())
                    .runFold(ByteString.empty(), (a,b) -> a.concat(b), materializer)
                ).succeedsWith(ByteString.fromString("{"
                    + "\"PersonId\":\"8fb40607-240a-4eb4-b847-db47c9c41751\","
                    + "\"Name\":{\"language\":\"en:US\",\"value\":\"John Smith\"},"
                    + "\"Address\":{\"Street\":\"Main st. 5\",\"City\":\"Bigtown\"},"
                    + "\"Parent\":{\"PersonId\":\"0f048c9f-063d-430e-af03-8012cd7667f1\",\"Name\":{\"language\":\"en:US\",\"value\":\"Jane Smith\"},"
                    + "\"Pet\":[{\"Name\":{\"value\":\"Alice\"}},{\"Name\":{\"value\":\"Bob\"}}]}"
                    + "}"));
            });
            
            it("should convert XML that has XSD-invalid tags, by ignoring those tags", () -> {
                assertThat(
                    StreamConverters.fromInputStream(() -> getClass().getResourceAsStream("person4.xml"))
                    .via(AaltoReader.instance)
                    .via(new XMLToJSON(model))
                    .via(JacksonWriter.flow())
                    .runFold(ByteString.empty(), (a,b) -> a.concat(b), materializer)
                ).succeedsWith(ByteString.fromString("{"
                    + "\"PersonId\":\"8fb40607-240a-4eb4-b847-db47c9c41751\","
                    + "\"Name\":{\"language\":\"en:US\",\"value\":\"John Smith\"},"
                    + "\"Address\":{\"Street\":\"Main st. 5\",\"City\":\"Bigtown\"},"
                    + "\"Pet\":[{\"Name\":{\"value\":\"Alice\"}},{\"Name\":{\"value\":\"Bob\"}}]"
                    + "}"));
            });
        });
    }
}

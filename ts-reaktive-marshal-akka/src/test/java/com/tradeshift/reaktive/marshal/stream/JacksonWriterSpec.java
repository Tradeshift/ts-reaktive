package com.tradeshift.reaktive.marshal.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.cuppa.Cuppa.describe;
import static org.forgerock.cuppa.Cuppa.it;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.forgerock.cuppa.junit.CuppaRunner;
import org.junit.runner.RunWith;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradeshift.reaktive.json.JSONEvent;
import com.tradeshift.reaktive.json.jackson.Jackson;
import com.tradeshift.reaktive.testkit.SharedActorSystemSpec;

import akka.stream.javadsl.Source;
import akka.util.ByteString;

@RunWith(CuppaRunner.class)
public class JacksonWriterSpec extends SharedActorSystemSpec {{
    describe("JacksonWriter", () -> {
        it("Should serialize parsed JSON events back to JSON", () -> {
            JsonParser input = new JsonFactory().createParser(getClass().getResource("/test.json"));
            List<JSONEvent> events = new ArrayList<>();
            while (input.nextToken() != null) {
                events.add(Jackson.getEvent(input));
            }
            
            ByteString result = Source.from(events).via(JacksonWriter.flow()).runFold(ByteString.empty(), (s1,s2) -> s1.concat(s2), materializer)
                .toCompletableFuture().get(1, TimeUnit.SECONDS);
            
            ObjectNode orig = new ObjectMapper().readValue(getClass().getResource("/test.json"), ObjectNode.class);
            ObjectNode test = new ObjectMapper().readValue(result.toArray(), ObjectNode.class);
            assertThat(test).isEqualTo(orig);
        });
    });
}
    

}

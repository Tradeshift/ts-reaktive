package com.tradeshift.reaktive.marshal.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.cuppa.Cuppa.describe;
import static org.forgerock.cuppa.Cuppa.it;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.forgerock.cuppa.junit.CuppaRunner;
import org.junit.runner.RunWith;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.tradeshift.reaktive.json.JSONEvent;
import com.tradeshift.reaktive.json.jackson.Jackson;
import com.tradeshift.reaktive.testkit.SharedActorSystemSpec;

import akka.stream.javadsl.FileIO;
import akka.stream.javadsl.Sink;

@RunWith(CuppaRunner.class)
public class ActsonReaderSpec extends SharedActorSystemSpec {{
    describe("ActsonReader", () -> {
        it("Should parse equivalent events to Jackson", () -> {
            JsonParser input = new JsonFactory().createParser(getClass().getResource("/test.json"));
            List<JSONEvent> events = new ArrayList<>();
            while (input.nextToken() != null) {
                events.add(Jackson.getEvent(input));
            }

            List<JSONEvent> result = FileIO.fromFile(new File(getClass().getResource("/test.json").getFile()))
                .via(ActsonReader.instance)
                .runWith(Sink.seq(), materializer)
                .toCompletableFuture().get(1, TimeUnit.SECONDS);
            
            assertThat(result).containsExactlyElementsOf(events);
        });
    });
}}

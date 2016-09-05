package com.tradeshift.reaktive.marshal.stream;

import static org.forgerock.cuppa.Cuppa.describe;
import static org.forgerock.cuppa.Cuppa.it;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.xmlunit.matchers.CompareMatcher.isIdenticalTo;

import java.io.File;
import java.util.concurrent.TimeUnit;

import org.forgerock.cuppa.junit.CuppaRunner;
import org.junit.runner.RunWith;
import org.xmlunit.builder.Input;

import com.tradeshift.reaktive.testkit.SharedActorSystemSpec;

import akka.stream.javadsl.FileIO;
import akka.util.ByteString;

@RunWith(CuppaRunner.class)
public class AaltoReaderSpec extends SharedActorSystemSpec {{
    describe("AaltoReader", () -> {
        it("Should parse XML input equivalent to Stax", () -> {
            ByteString result = FileIO.fromFile(new File(getClass().getResource("/test.xml").getFile()))
                .via(AaltoReader.instance)
                .via(StaxWriter.flow())
                .runFold(ByteString.empty(), (s1,s2) -> s1.concat(s2), materializer)
                .toCompletableFuture().get(1, TimeUnit.SECONDS);
            
            assertThat(result.toArray(), isIdenticalTo(Input.fromURL(getClass().getResource("/test.xml"))));
        });
    });
}}

package com.tradeshift.reaktive.csv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.cuppa.Cuppa.describe;
import static org.forgerock.cuppa.Cuppa.it;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.forgerock.cuppa.junit.CuppaRunner;
import org.junit.runner.RunWith;

import com.tradeshift.reaktive.testkit.SharedActorSystemSpec;

import akka.stream.javadsl.Source;
import akka.util.ByteString;

@RunWith(CuppaRunner.class)
public class CharsetDecoderFlowSpec extends SharedActorSystemSpec {{
    describe("CharsetDecoderFlow", () -> {
        it("Should decode UTF-16 from a single byte string", () -> {
            assertThat(
                Source.from(Arrays.asList(ByteString.fromInts(72, 0, 101, 0, 108, 0, 108, 0, 111, 0, 44, 0, 32, 0, 119, 0, 111, 0, 114, 0, 108, 0, 100, 0, 33, 0)))
                .via(new CharsetDecoderFlow(Charset.forName("UTF-16LE")))
                .runFold("", (s1,s2) -> s1 + s2, materializer)
                .toCompletableFuture()
                .get(1, TimeUnit.SECONDS)
            ).isEqualTo("Hello, world!");
        });
        
        it("Should decode UTF-16 that is delivered in buffers of 1", () -> {
            assertThat(
                Source.from(Arrays.asList(72, 0, 101, 0, 108, 0, 108, 0, 111, 0, 44, 0, 32, 0, 119, 0, 111, 0, 114, 0, 108, 0, 100, 0, 33, 0))
                .map(i -> ByteString.fromInts(i))
                .via(new CharsetDecoderFlow(Charset.forName("UTF-16LE")))
                .runFold("", (s1,s2) -> s1 + s2, materializer)
                .toCompletableFuture()
                .get(1, TimeUnit.SECONDS)
            ).isEqualTo("Hello, world!");
        });
    });
}}

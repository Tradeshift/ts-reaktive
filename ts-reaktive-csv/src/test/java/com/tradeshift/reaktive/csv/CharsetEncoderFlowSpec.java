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
public class CharsetEncoderFlowSpec extends SharedActorSystemSpec {{
    describe("CharsetEncoderFlow", () -> {
        it("Should encode UTF-16 from a single string", () -> {
            assertThat(
                Source.single("Hello, world!")
                .via(new CharsetEncoderFlow(Charset.forName("UTF-16LE")))
                .runFold(ByteString.empty(), (s1,s2) -> s1.concat(s2), materializer)
                .toCompletableFuture()
                .get(1, TimeUnit.SECONDS)
            ).isEqualTo(ByteString.fromInts(72, 0, 101, 0, 108, 0, 108, 0, 111, 0, 44, 0, 32, 0, 119, 0, 111, 0, 114, 0, 108, 0, 100, 0, 33, 0));
        });
        
        it("Should encode UTF-16 that is delivered in buffers of 1", () -> {
            assertThat(
                Source.from(Arrays.asList("Hello, world!".toCharArray())).map(String::valueOf)
                .via(new CharsetEncoderFlow(Charset.forName("UTF-16LE")))
                .runFold(ByteString.empty(), (s1,s2) -> s1.concat(s2), materializer)
                .toCompletableFuture()
                .get(1, TimeUnit.SECONDS)
            ).isEqualTo(ByteString.fromInts(72, 0, 101, 0, 108, 0, 108, 0, 111, 0, 44, 0, 32, 0, 119, 0, 111, 0, 114, 0, 108, 0, 100, 0, 33, 0));
        });
    });
}}

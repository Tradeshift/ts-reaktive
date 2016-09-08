package com.tradeshift.reaktive.marshal.stream;

import static org.forgerock.cuppa.Cuppa.describe;
import static org.forgerock.cuppa.Cuppa.it;

import java.util.Arrays;

import org.forgerock.cuppa.junit.CuppaRunner;
import org.junit.runner.RunWith;

import com.tradeshift.reaktive.testkit.SharedActorSystemSpec;
import static com.tradeshift.reaktive.assertj.CompletionStageAssertions.assertThat;

import akka.stream.javadsl.Source;

@RunWith(CuppaRunner.class)
public class Base64DecoderSpec extends SharedActorSystemSpec {{
    describe("Base64Decoder", () -> {
        it("should decode base64, hopping over illegal characters", () -> {
            assertThat(
                Source.single("aGVsbG8=")
                .via(Base64Decoder.decodeBase64Strings)
                .runFold("", (s,b) -> s + b.utf8String(), materializer)
            ).succeedsWith("hello");
        });
        
        it("should decode fine if the input is split on none-4-character boundaries", () -> {
            assertThat(
                Source.from(Arrays.asList("a","G","Vsb","G8="))
                .via(Base64Decoder.decodeBase64Strings)
                .runFold("", (s,b) -> s + b.utf8String(), materializer)
            ).succeedsWith("hello");
        });
        
        it("should blow up on incomplete input", () -> {
            assertThat(
                Source.single("aGVsbG=")
                .via(Base64Decoder.decodeBase64Strings)
                .runFold("", (s,b) -> s + b.utf8String(), materializer)
            ).failure().hasMessageContaining("Illegal base64 ending sequence");
        });
    });
}}

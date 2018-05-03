package com.tradeshift.reaktive.marshal.stream;

import static akka.util.ByteString.fromString;
import static com.tradeshift.reaktive.assertj.CompletionStageAssertions.assertThat;
import static org.forgerock.cuppa.Cuppa.describe;
import static org.forgerock.cuppa.Cuppa.it;

import java.util.Arrays;

import org.forgerock.cuppa.junit.CuppaRunner;
import org.junit.runner.RunWith;

import com.tradeshift.reaktive.testkit.SharedActorSystemSpec;
import akka.stream.javadsl.Source;

@RunWith(CuppaRunner.class)
public class Base64FlowsSpec extends SharedActorSystemSpec {{
    describe("encoder", () -> {
        it("should encode to correct base64", () -> {
            assertThat(
                Source.single(fromString("hello there"))
                .via(Base64Flows.encodeStrings)
                .runFold("", (s,b) -> s + b, materializer)
            ).succeedsWith("aGVsbG8gdGhlcmU=");
        });
        
        it("should encode fine if the input is split on none-3-byte boundaries", () -> {
            assertThat(
                Source.from(Arrays.asList(fromString("h"), fromString("e"), fromString("ll"), fromString("o")))
                .via(Base64Flows.encodeStrings)
                .runFold("", (s,b) -> s + b, materializer)
            ).succeedsWith("aGVsbG8=");
        });
    });
    
    describe("decoder", () -> {
        it("should decode base64, hopping over illegal characters", () -> {
            assertThat(
                Source.single("aGVsbG8=")
                .via(Base64Flows.decodeStrings)
                .runFold("", (s,b) -> s + b.utf8String(), materializer)
            ).succeedsWith("hello");
        });
        
        it("should decode fine if the input is split on none-4-character boundaries", () -> {
            assertThat(
                Source.from(Arrays.asList("a","G","Vsb","G8="))
                .via(Base64Flows.decodeStrings)
                .runFold("", (s,b) -> s + b.utf8String(), materializer)
            ).succeedsWith("hello");
        });
        
        it("should decode fine if the input is split on none-4-character boundaries that include whitespace", () -> {
            assertThat(
                Source.from(Arrays.asList("a","G","  Vsb ","G8="))
                .via(Base64Flows.decodeStrings)
                .runFold("", (s,b) -> s + b.utf8String(), materializer)
            ).succeedsWith("hello");
        });
        
        it("should blow up on incomplete input", () -> {
            assertThat(
                Source.single("aGVsbG=")
                .via(Base64Flows.decodeStrings)
                .runFold("", (s,b) -> s + b.utf8String(), materializer)
            ).failure().isInstanceOf(IllegalArgumentException.class);
        });
    });
}}

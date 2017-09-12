package com.tradeshift.reaktive.protobuf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.forgerock.cuppa.Cuppa.describe;
import static org.forgerock.cuppa.Cuppa.it;

import java.util.concurrent.TimeUnit;

import org.forgerock.cuppa.junit.CuppaRunner;
import org.junit.runner.RunWith;

import com.tradeshift.reaktive.testkit.SharedActorSystemSpec;

import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import io.vavr.collection.Vector;

@RunWith(CuppaRunner.class)
public class DelimitedProtobufFramingSpec extends SharedActorSystemSpec {{
    describe("DelimitedProtobufFraming", () -> {
        it("should parse multiple protobufs in one chunk", () -> {
            assertThat(Source
                .single(ByteString.fromInts(4,0,0,0,0,2,1,1))
                .via(DelimitedProtobufFraming.instance)
                .runWith(Sink.seq(), materializer)
                .toCompletableFuture()
                .get(1, TimeUnit.SECONDS)
            ).containsExactly(ByteString.fromInts(0,0,0,0), ByteString.fromInts(1,1));
        });
        
        it("should parse multiple protobufs made up of 1-byte strings", () -> {
            assertThat(Source
                .from(Vector.of(4,0,0,0,0,2,1,1).map(i -> ByteString.fromInts(i)))
                .via(DelimitedProtobufFraming.instance)
                .runWith(Sink.seq(), materializer)
                .toCompletableFuture()
                .get(1, TimeUnit.SECONDS)
            ).containsExactly(ByteString.fromInts(0,0,0,0), ByteString.fromInts(1,1));
        });
        
        it("should fail on an invalid delimiter once the stream reaches at least 10 bytes", () -> {
            assertThatThrownBy(() -> Source
                .single(ByteString.fromInts(255,255,255,255,255,255,255,255,255,255))
                .via(DelimitedProtobufFraming.instance)
                .runWith(Sink.seq(), materializer)
                .toCompletableFuture()
                .get(1, TimeUnit.SECONDS)
            ).hasMessageContaining("malformed varint");
        });
    });
}}

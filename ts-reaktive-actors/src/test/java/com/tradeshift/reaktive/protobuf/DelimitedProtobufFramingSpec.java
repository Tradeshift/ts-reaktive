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

        it("should emit the deframed messages even if the stream is splitted in the middle of a size frame", () -> {
            //1000 as a 2-byte unsigned int (used in delimited protobuf serialization)
            ByteString thousand = ByteString.fromInts(-24, 7); 
            ByteString aThousandOnes = Vector.fill(1000, () -> ByteString.fromInts(1))
                .fold(ByteString.empty(), ByteString::concat);
            assertThat(Source
                    .from(Vector.of(4, 0, 0, 0, 0).map(ByteString::fromInts)
                        .append(thousand)
                        .append(aThousandOnes))
                    .fold(ByteString.empty(), ByteString::concat)
                    .mapConcat(bs -> {
                        byte[] bytes = bs.toArray();
                        //split from the middle of the second size frame (1000)
                        byte[] part1 = new byte[6];
                        byte[] part2 = new byte[1001];
                        System.arraycopy(bytes, 0, part1, 0, 6);
                        System.arraycopy(bytes, 6, part2, 0, 1001);
                        return Vector.of(ByteString.fromArray(part1), ByteString.fromArray(part2));
                    })
                    .via(DelimitedProtobufFraming.instance)
                    .runWith(Sink.seq(), materializer)
                    .toCompletableFuture()
                    .get(1, TimeUnit.SECONDS)
            ).containsExactly(ByteString.fromInts(0, 0, 0, 0), aThousandOnes);
        });
    });
}}

package com.tradeshift.reaktive.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.cuppa.Cuppa.describe;
import static org.forgerock.cuppa.Cuppa.it;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.forgerock.cuppa.junit.CuppaRunner;
import org.junit.runner.RunWith;

import com.tradeshift.reaktive.backup.S3.S3ListResponse;
import com.tradeshift.reaktive.marshal.stream.AaltoReader;
import com.tradeshift.reaktive.marshal.stream.ProtocolReader;
import com.tradeshift.reaktive.testkit.SharedActorSystemSpec;

import akka.stream.javadsl.Sink;
import akka.stream.javadsl.StreamConverters;

@RunWith(CuppaRunner.class)
public class S3Spec extends SharedActorSystemSpec {
    {
        describe("S3ListResponse", () -> {
            it("should be parseable from a real S3 list response", () -> {
                List<S3ListResponse> list = StreamConverters.fromInputStream(() -> getClass().getResourceAsStream("listresponse.xml"))
                    .via(AaltoReader.instance)
                    .via(ProtocolReader.of(S3ListResponse.proto))
                    .runWith(Sink.seq(), materializer)
                    .toCompletableFuture()
                    .get(1, TimeUnit.SECONDS);
                
                assertThat(list).hasSize(1);
                assertThat(list.get(0).getNextContinuationToken().isDefined()).isFalse();
                assertThat(list.get(0).getEntries()).hasSize(1);
                assertThat(list.get(0).getEntries().head().getKey()).isEqualTo("S3BackupIntegrationSpec/1478683083999/MyEvent-from-19700101000000003");
                assertThat(list.get(0).getEntries().head().getSize()).isEqualTo(296l);
                assertThat(list.get(0).getEntries().head().getLastModified()).isEqualTo(Instant.parse("2016-11-09T09:18:09.001Z"));
            });
        });
    }
}

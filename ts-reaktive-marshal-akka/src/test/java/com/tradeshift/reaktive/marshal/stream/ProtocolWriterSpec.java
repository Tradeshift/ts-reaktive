package com.tradeshift.reaktive.marshal.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.cuppa.Cuppa.describe;
import static org.forgerock.cuppa.Cuppa.it;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.forgerock.cuppa.junit.CuppaRunner;
import org.junit.runner.RunWith;

import com.tradeshift.reaktive.json.JSONEvent;
import com.tradeshift.reaktive.marshal.WriteProtocol;
import com.tradeshift.reaktive.marshal.Writer;
import com.tradeshift.reaktive.testkit.SharedActorSystemSpec;

import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import io.vavr.collection.Vector;

@SuppressWarnings("unchecked")
@RunWith(CuppaRunner.class)
public class ProtocolWriterSpec extends SharedActorSystemSpec {{
    describe("ProtocolWriter", () -> {
        it("Should emit all the elements a write protocol emits", () -> {
            WriteProtocol<JSONEvent,String> proto = mock(WriteProtocol.class);
            Writer<JSONEvent, String> writer = mock(Writer.class);
            when(proto.writer()).thenReturn(writer);
            when(writer.apply("hello")).thenReturn(Vector.of(JSONEvent.START_ARRAY, new JSONEvent.StringValue("hello")));
            when(writer.apply("world")).thenReturn(Vector.of(new JSONEvent.StringValue("world")));
            when(writer.reset()).thenReturn(Vector.of(JSONEvent.END_ARRAY));
            
            List<JSONEvent> result = Source.from(Arrays.asList("hello", "world")).via(ProtocolWriter.of(proto)).runWith(Sink.seq(), materializer)
                .toCompletableFuture().get(1, TimeUnit.SECONDS);
            assertThat(result).containsExactly(
                JSONEvent.START_ARRAY, new JSONEvent.StringValue("hello"), new JSONEvent.StringValue("world"), JSONEvent.END_ARRAY
            );
        });
    });
}}

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
import com.tradeshift.reaktive.marshal.ReadProtocol;
import com.tradeshift.reaktive.marshal.Reader;
import com.tradeshift.reaktive.testkit.SharedActorSystemSpec;

import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import io.vavr.control.Try;

@SuppressWarnings("unchecked")
@RunWith(CuppaRunner.class)
public class ProtocolReaderSpec extends SharedActorSystemSpec {{
    describe("ProtocolReader", () -> {
        it("Should emit all the instances a read protocol emits", () -> {
            ReadProtocol<JSONEvent,String> proto = mock(ReadProtocol.class);
            Reader<JSONEvent, String> reader = mock(Reader.class);
            when(proto.reader()).thenReturn(reader);
            when(reader.apply(JSONEvent.START_ARRAY)).thenReturn(ReadProtocol.none());
            when(reader.apply(JSONEvent.END_ARRAY)).thenReturn(ReadProtocol.none());
            when(reader.apply(new JSONEvent.StringValue("hello"))).thenReturn(Try.success("hello"));
            when(reader.apply(new JSONEvent.StringValue("world"))).thenReturn(Try.success("world"));
            when(reader.reset()).thenReturn(ReadProtocol.none());
            
            List<String> result = Source.from(Arrays.asList(
                JSONEvent.START_ARRAY, new JSONEvent.StringValue("hello"), new JSONEvent.StringValue("world"), JSONEvent.END_ARRAY))
                .via(ProtocolReader.of(proto))
                .runWith(Sink.seq(), materializer)
                .toCompletableFuture().get(1, TimeUnit.SECONDS);
            
            assertThat(result).containsExactly("hello", "world");
        });
    });
}}

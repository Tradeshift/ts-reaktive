package com.tradeshift.reaktive.marshal.http;

import static akka.http.javadsl.server.Directives.complete;
import static akka.http.javadsl.server.Directives.completeOK;
import static akka.http.javadsl.server.Directives.entity;
import static akka.http.javadsl.server.Directives.onSuccess;
import static com.tradeshift.reaktive.json.JSONProtocol.array;
import static com.tradeshift.reaktive.json.JSONProtocol.booleanValue;
import static com.tradeshift.reaktive.json.JSONProtocol.field;
import static com.tradeshift.reaktive.json.JSONProtocol.object;
import static com.tradeshift.reaktive.json.JSONProtocol.stringValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.cuppa.Cuppa.describe;
import static org.forgerock.cuppa.Cuppa.it;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.forgerock.cuppa.junit.CuppaRunner;
import org.junit.runner.RunWith;

import com.tradeshift.reaktive.json.JSONEvent;
import com.tradeshift.reaktive.marshal.Protocol;
import com.tradeshift.reaktive.testkit.HttpIntegrationSpec;

import akka.NotUsed;
import akka.http.javadsl.model.HttpEntity.Strict;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.Route;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

@RunWith(CuppaRunner.class)
public class JSONMarshallersSpec extends HttpIntegrationSpec {{
    Protocol<JSONEvent,String> proto = array(
        object(
            field("item", stringValue)
        )
        .having(
            field("important", booleanValue), true
        )
    );
    
    describe("JSONMarshallers.sourceFromJSON", () -> {
        Route route = entity(JSONMarshallers.sourceFromJSON(proto), source ->
            onSuccess(() -> source.runWith(Sink.fold("", (s1, s2) -> s1 + s2), materializer), s ->
                complete(s)
            )
        );
        
        it("Should unmarshal valid json into individual string objects", () -> {
            serve(route, client -> {
                Strict result = client.postJSON("/", "[{\"item\":\"hello\",\"important\":true},{\"item\":\"world\",\"important\":true}]");
                assertThat(result.getData().utf8String()).isEqualTo("helloworld");
            });
        });
        
        it("Should fail if the json doesn't match the protocol", () -> {
            serve(route, client -> {
                HttpResponse result = client.tryPostJSON("/", "[{\"item\":\"hello\"},{\"item\":\"world\",\"important\":true}]");
                // FIXME this should really yield a 400 instead, see https://github.com/akka/akka-http/issues/12
                assertThat(result.status()).isEqualTo(StatusCodes.INTERNAL_SERVER_ERROR);
            });
        });
    });
    
    describe("JSONMarshallers.fromJSON", () -> {
        Route route = entity(JSONMarshallers.fromJSON(proto), s ->
            complete(s)
        );
        
        it("Should should unmarshal valid json, ignoring further elements", () -> {
            serve(route, client -> {
                Strict result = client.postJSON("/", "[{\"item\":\"hello\",\"important\":true},{\"item\":\"world\",\"important\":true}]");
                assertThat(result.getData().utf8String()).isEqualTo("hello");
            });
        });
        
        it("Should fail if the json doesn't match the protocol", () -> {
            serve(route, client -> {
                HttpResponse result = client.tryPostJSON("/", "[{\"item\":\"hello\"},{\"item\":\"world\",\"important\":true}]");
                String body = result.entity().toStrict(1000, materializer).toCompletableFuture().get(1, TimeUnit.SECONDS).getData().utf8String();
                assertThat(result.status()).isEqualTo(StatusCodes.BAD_REQUEST);
                assertThat(body).contains("must have field important");
            });
        });
    });
    
    describe("JSONMarshallers.sourceToJSON", () -> {
        Source<String, NotUsed> src = Source.from(Arrays.asList("hello", "world"));
        Route route = completeOK(src, JSONMarshallers.sourceToJSON(proto));
        
        it("should marshal valid json including all of the source elements", () -> {
            serve(route, client -> {
                assertThat(client.getByteString("/").utf8String()).isEqualTo("[{\"item\":\"hello\",\"important\":true},{\"item\":\"world\",\"important\":true}]");
            });
        });
    });
    
    describe("JSONMarshallers.toJSON", () -> {
        Route route = completeOK("hello", JSONMarshallers.toJSON(proto));
        
        it("should marshal valid json with one element according to the protocol", () -> {
            serve(route, client -> {
                assertThat(client.getByteString("/").utf8String()).isEqualTo("[{\"item\":\"hello\",\"important\":true}]");
            });
        });
    });
    
}}

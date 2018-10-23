package com.tradeshift.reaktive.akka.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.forgerock.cuppa.Cuppa.describe;
import static org.forgerock.cuppa.Cuppa.it;
import static org.forgerock.cuppa.Cuppa.when;

import java.time.Instant;

import org.forgerock.cuppa.junit.CuppaRunner;
import org.junit.runner.RunWith;

import com.tradeshift.reaktive.akka.UUIDs;
import com.tradeshift.reaktive.testkit.HttpIntegrationSpec;

import akka.http.javadsl.marshalling.Marshaller;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.persistence.query.EventEnvelope;
import akka.persistence.query.TimeBasedUUID;
import akka.persistence.query.javadsl.EventsByTagQuery;
import akka.stream.StreamTcpException;
import akka.stream.javadsl.Source;
import akka.util.ByteString;

@RunWith(CuppaRunner.class)
public class EventRouteIntegrationSpec extends HttpIntegrationSpec {
    
    private EventRoute testEventRoute(EventsByTagQuery journal) {
        Marshaller<Source<EventEnvelope, ?>, HttpResponse> marshaller = Marshaller.opaque(src -> {
            Source<ByteString, ?> bytes = src.map(e -> ByteString.fromString("test"));
            return HttpResponse.create().withEntity(HttpEntities.create(ContentTypes.APPLICATION_OCTET_STREAM, bytes));
        });
        return new EventRoute(materializer, journal, marshaller, "testEvent");
    }
    
{
    describe("GET /events", () -> {
        when("the underlying source encounters an error before the first element", () -> {
            EventRoute eventRoute = testEventRoute((tag, idx) ->
                Source.failed(new RuntimeException("simulated failure on first element"))
            );
            
            it("should fail the http request early by returning a 500 status code", () -> {
                serve(eventRoute.apply(), http -> {
                    assertThat(http.tryGet("/").status()).isEqualTo(StatusCodes.INTERNAL_SERVER_ERROR);
                });
            });
        });
        
        when("the underlying source completes with error after events have been sent out", () -> {
            EventRoute eventRoute = testEventRoute((tag, idx) -> {
                return Source.repeat(
                    EventEnvelope.apply(new TimeBasedUUID(UUIDs.startOf(1)), "doc_bc779420-beac-4636-b747-ea0a587d4f8b", 1, "hello")
                ).concat(
                    Source.failed(new RuntimeException("simulated failure mid-stream"))
                );
            });
            
            it("should reset the TCP connection from the server side", () -> {
                assertThatThrownBy(() ->
                    serve(eventRoute.apply(), http -> http.getByteString("/"))
                ).hasMessageContaining("reset by peer")
                 .isInstanceOf(StreamTcpException.class);
            });
        });
        
        it("should reject if given timestamp is not valid", () -> {
            assertThatThrownBy(() -> 
                serve(testEventRoute((tag, idx) -> Source.empty()).apply(), http -> http.getByteString("/?since=123a"))
            ).hasMessageContaining(
                "The query parameter 'since' was malformed:\n"
              + "Timestamp must either be in epoch millisconds or ISO-8601");
        });
        
    });
    
    describe("EventRoute.toInstant", () -> {
        it("converts epoch millis to Instant", () -> {
            String since = "12345";
            Instant instant = EventRoute.toInstant(since);
            assertThat(instant.toEpochMilli()).isEqualTo(12345L);
        });

        it("converts ISO-8601 timestamp to Instant", () -> {
            String since = "1970-01-01T00:00:30Z";
            Instant instant = EventRoute.toInstant(since);
            assertThat(instant.toEpochMilli()).isEqualTo(30000L);
            assertThat(instant.toString()).isEqualTo(since);
        });
    });
}}

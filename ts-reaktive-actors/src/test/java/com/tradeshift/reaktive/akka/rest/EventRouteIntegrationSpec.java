package com.tradeshift.reaktive.akka.rest;

import org.forgerock.cuppa.junit.CuppaRunner;
import static org.forgerock.cuppa.Cuppa.describe;
import static org.forgerock.cuppa.Cuppa.when;
import static org.forgerock.cuppa.Cuppa.it;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.runner.RunWith;

import akka.http.javadsl.model.StatusCodes;
import akka.persistence.query.EventEnvelope;
import akka.persistence.query.javadsl.EventsByTagQuery;
import akka.stream.StreamTcpException;
import akka.stream.javadsl.Source;
import akka.util.ByteString;

import com.tradeshift.reaktive.testkit.HttpIntegrationSpec;

@RunWith(CuppaRunner.class)
public class EventRouteIntegrationSpec extends HttpIntegrationSpec {
    static class TestEventRoute extends EventRoute {
        public TestEventRoute(EventsByTagQuery journal) {
            super(materializer, journal, "testEvent");
        }

        @Override
        protected ByteString serialize(EventEnvelope envelope) {
            return ByteString.fromString(envelope.event().toString());
        }
    }
    
{
    describe("GET /events", () -> {
        when("the underlying source encounters an error before the first element", () -> {
            TestEventRoute eventRoute = new TestEventRoute((tag, idx) -> 
                Source.failed(new RuntimeException("simulated failure on first element"))
            );
            
            it("should fail the http request early by returning a 500 status code", () -> {
                serve(eventRoute.apply(), http -> {
                    assertThat(http.tryGet("/events").status()).isEqualTo(StatusCodes.INTERNAL_SERVER_ERROR);
                });
            });
        });
        
        when("the underlying source completes with error after events have been sent out", () -> {
            TestEventRoute eventRoute = new TestEventRoute((tag, idx) -> {
                return Source.single(
                    EventEnvelope.apply(0, "doc_bc779420-beac-4636-b747-ea0a587d4f8b", 1, "hello")
                ).concat(
                    Source.failed(new RuntimeException("simulated failure mid-stream"))
                );
            });
            
            it("should reset the TCP connection from the server side", () -> {
                assertThatThrownBy(() ->
                    serve(eventRoute.apply(), http -> http.getByteString("/events"))
                ).hasMessageContaining("reset by peer")
                 .isInstanceOf(StreamTcpException.class);
            });
        });
        
    });    
}}

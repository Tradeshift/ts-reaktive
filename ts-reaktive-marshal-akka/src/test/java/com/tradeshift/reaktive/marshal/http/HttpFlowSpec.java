package com.tradeshift.reaktive.marshal.http;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.tradeshift.reaktive.assertj.CompletionStageAssertions.assertThat;
import static org.forgerock.cuppa.Cuppa.after;
import static org.forgerock.cuppa.Cuppa.beforeEach;
import static org.forgerock.cuppa.Cuppa.describe;
import static org.forgerock.cuppa.Cuppa.it;

import java.util.concurrent.CompletionStage;

import org.forgerock.cuppa.junit.CuppaRunner;
import org.junit.runner.RunWith;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.tradeshift.reaktive.testkit.SharedActorSystemSpec;

import akka.Done;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpMethods;
import akka.http.javadsl.model.Uri;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.util.ByteString;

@RunWith(CuppaRunner.class)
public class HttpFlowSpec extends SharedActorSystemSpec {{
    
    WireMockServer wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
    wireMockServer.start();
    System.out.println("Started wiremock on port " + wireMockServer.port());
    WireMock.configureFor(wireMockServer.port());
    
    describe("HttpFlow.create", () -> {
        beforeEach(() -> {
            WireMock.reset();
        });
        
        after(() -> {
            wireMockServer.stop();
        });
        
        it("should make a real http request when materialized, and make the same request again if rematerialized", () -> {
            stubFor(put(urlMatching(".*")).willReturn(aResponse().withStatus(201).withBody("response")));
            
            HttpFlow httpFlow = new HttpFlow(Http.get(system), materializer);
            Flow<ByteString, ByteString, CompletionStage<Done>> flow = httpFlow.flow(HttpMethods.PUT, Uri.create("http://localhost:" + wireMockServer.port()), ContentTypes.APPLICATION_JSON);
            
            assertThat(
                Source.single(ByteString.fromString("request"))
                .via(flow)
                .runFold("", (s,b) -> s + b.utf8String(), materializer)
            ).succeedsWith("response");
            
            WireMock.reset();
            stubFor(put(urlMatching(".*")).willReturn(aResponse().withStatus(200).withBody("iwashere")));
            
            assertThat(
                Source.single(ByteString.fromString("request"))
                .via(flow)
                .runFold("", (s,b) -> s + b.utf8String(), materializer)
            ).succeedsWith("iwashere");
        });
        
        it("should fail the stream if the http request could not be made", () -> {
            HttpFlow httpFlow = new HttpFlow(Http.get(system), materializer);
            Flow<ByteString, ByteString, CompletionStage<Done>> flow = httpFlow.flow(HttpMethods.PUT, Uri.create("http://localhost:1"), ContentTypes.APPLICATION_JSON);
            
            assertThat(
                Source.single(ByteString.fromString("request"))
                .via(flow)
                .runWith(Sink.ignore(), materializer)
            ).fails(); // actual way akka fails not connecting to port :1 apparently depends on timing
        });
        
        it("should fail the stream if the http request yielded a non-success response", () -> {
            stubFor(put(urlMatching(".*")).willReturn(aResponse().withStatus(400).withBody("computer says no")));
            
            HttpFlow httpFlow = new HttpFlow(Http.get(system), materializer);
            Flow<ByteString, ByteString, CompletionStage<Done>> flow = httpFlow.flow(HttpMethods.PUT, Uri.create("http://localhost:" + wireMockServer.port()), ContentTypes.APPLICATION_JSON);
            
            assertThat(
                Source.single(ByteString.fromString("request"))
                .via(flow)
                .runWith(Sink.ignore(), materializer)
            ).failure()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("400");
            
        });
    });
}}
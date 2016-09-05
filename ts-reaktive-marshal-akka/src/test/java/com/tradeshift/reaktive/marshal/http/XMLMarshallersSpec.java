package com.tradeshift.reaktive.marshal.http;

import static akka.http.javadsl.server.Directives.complete;
import static akka.http.javadsl.server.Directives.completeOK;
import static akka.http.javadsl.server.Directives.entity;
import static akka.http.javadsl.server.Directives.onSuccess;
import static com.tradeshift.reaktive.xml.XMLProtocol.attribute;
import static com.tradeshift.reaktive.xml.XMLProtocol.body;
import static com.tradeshift.reaktive.xml.XMLProtocol.qname;
import static com.tradeshift.reaktive.xml.XMLProtocol.tag;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.cuppa.Cuppa.describe;
import static org.forgerock.cuppa.Cuppa.it;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import javax.xml.stream.events.XMLEvent;

import org.forgerock.cuppa.junit.CuppaRunner;
import org.junit.runner.RunWith;

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
public class XMLMarshallersSpec extends HttpIntegrationSpec {{
    Protocol<XMLEvent,String> proto = tag(qname("root"),
        tag(qname("element"),
            body
        )
        .having(
            attribute("important"), "true"
        )
    );

    describe("XMLMarshallers.sourceFromXML", () -> {
        Route route = entity(XMLMarshallers.sourceFromXML(proto), source ->
            onSuccess(() -> source.runWith(Sink.fold("", (s1, s2) -> s1 + s2), materializer), s ->
                complete(s)
            )
        );
        
        it("Should unmarshal valid xml into individual string objects", () -> {
            serve(route, client -> {
                Strict result = client.postXML("/", "<root><element important='true'>hello</element><element important='true'>world</element></root>");
                assertThat(result.getData().utf8String()).isEqualTo("helloworld");
            });
        });
        
        it("Should fail if the xml doesn't match the protocol", () -> {
            serve(route, client -> {
                HttpResponse result = client.tryPostXML("/", "<root><element important='true'>hello</element><element>world</element></root>");
                // FIXME this should really yield a 400 instead, see https://github.com/akka/akka-http/issues/12
                assertThat(result.status()).isEqualTo(StatusCodes.INTERNAL_SERVER_ERROR);
            });
        });
    });
    
    describe("XMLMarshallers.fromXML", () -> {
        Route route = entity(XMLMarshallers.fromXML(proto), s ->
            complete(s)
        );
        
        it("Should should unmarshal valid xml, ignoring further elements", () -> {
            serve(route, client -> {
                Strict result = client.postXML("/", "<root><element important='true'>hello</element><element important='true'>world</element></root>");
                assertThat(result.getData().utf8String()).isEqualTo("hello");
            });
        });
        
        it("Should fail if the xml of the first element doesn't match the protocol", () -> {
            serve(route, client -> {
                HttpResponse result = client.tryPostXML("/", "<root><element>hello</element><element important='true'>world</element></root>");
                String body = result.entity().toStrict(1000, materializer).toCompletableFuture().get(1, TimeUnit.SECONDS).getData().utf8String();
                assertThat(result.status()).isEqualTo(StatusCodes.BAD_REQUEST);
                assertThat(body).contains("must have @important");
            });
        });
    });
    
    describe("XMLMarshallers.sourceToXML", () -> {
        Source<String, NotUsed> src = Source.from(Arrays.asList("hello", "world"));
        Route route = completeOK(src, XMLMarshallers.sourceToXML(proto));
        
        it("should marshal valid xml including all of the source elements", () -> {
            serve(route, client -> {
                assertThat(client.getByteString("/").utf8String()).isEqualTo("<root><element important=\"true\">hello</element><element important=\"true\">world</element></root>");
            });
        });
    });
    
    describe("XMLMarshallers.toXML", () -> {
        Route route = completeOK("hello", XMLMarshallers.toXML(proto));
        
        it("should marshal valid xml with one element according to the protocol", () -> {
            serve(route, client -> {
                assertThat(client.getByteString("/").utf8String()).isEqualTo("<root><element important=\"true\">hello</element></root>");
            });
        });
    });
    
}}

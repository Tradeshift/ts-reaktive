package com.tradeshift.reaktive.testkit;

import static akka.http.javadsl.server.Directives.complete;
import static akka.http.javadsl.server.Directives.extractRequest;
import static akka.http.javadsl.server.Directives.onSuccess;
import static akka.http.javadsl.server.Directives.post;
import static org.forgerock.cuppa.Cuppa.describe;
import static org.forgerock.cuppa.Cuppa.it;
import static org.forgerock.cuppa.Cuppa.when;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.forgerock.cuppa.junit.CuppaRunner;
import org.junit.runner.RunWith;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import com.tradeshift.reaktive.marshal.stream.AaltoReader;
import com.tradeshift.reaktive.marshal.stream.StaxWriter;

import akka.Done;
import akka.http.javadsl.model.HttpEntity;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.Route;
import akka.stream.javadsl.AsPublisher;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.util.ByteString;

@RunWith(CuppaRunner.class)
public class HttpMinimalFailureSpec extends HttpIntegrationSpec {
    
    private final Sink<ByteString,CompletionStage<Long>> counter =
        Flow.of(ByteString.class)
            .toMat(Sink.fold(0l, (count, b) -> count + b.size()), (m1,m2) -> m2);
    
    /**
     * Returns a sink that immediately fails asynchronously when materialized.
     * This is a somewhat convoluted example, but it's used in HttpFlow to allow us to expose an HTTP upload target as a Sink.
     **/
    Sink<ByteString, CompletionStage<Done>> failingSink() {
        Sink<ByteString, Publisher<ByteString>> in = Sink.asPublisher(AsPublisher.WITHOUT_FANOUT);
        Source<ByteString, Subscriber<ByteString>> out = Source.asSubscriber();
        
        return Flow.fromSinkAndSourceMat(in, out, Keep.both()).mapMaterializedValue(pair -> {
            return CompletableFuture.<Done>supplyAsync(() -> {
                RuntimeException x = new RuntimeException("Simulated failure");
                pair.second().onError(x);
                throw x;
            });
        }).toMat(Sink.ignore(), (m1,m2) -> m1);
    }
    
    CompletionStage<Done> failImmediate(HttpEntity entity) {
        Sink<ByteString, CompletionStage<Done>> sink = failingSink();
        
        return entity.getDataBytes()
            .log("data")
            .alsoToMat(counter, (m1,m2) -> m2)
            .via(AaltoReader.instance)
            .log("counted")
            .via(StaxWriter.flow())
            .<CompletionStage<Done>, CompletionStage<Done>>toMat(sink, (m1,m2) -> m2)
            .run(materializer);
    }
    
    {
        describe("A route that parses its request as XML, re-serializes it, and then tries to write it to a non-open HTTP port", () -> {
            final Route route = post(() ->
                extractRequest(request -> {
                    System.out.println(request);
                    return onSuccess(() -> failImmediate(request.entity()), result ->
                        complete(StatusCodes.NO_CONTENT)
                    );
                })
            );
            
            when("Uploading a rather large stream", () -> {
                it("should complete without logging extra errors", () -> {
                    serve(route, client -> {
                        int port = Integer.parseInt(client.baseUrl.substring(client.baseUrl.lastIndexOf(':') + 1));
                        Socket s = new Socket("localhost", port);
                        OutputStream out = s.getOutputStream();
                        out.write((
                            "POST / HTTP/1.1\r\n" +
                            "Host: localhost:" + port + "\r\n" +
                            "User-Agent: curl/7.50.3\r\n" +
                            "Accept: */*\r\n" +
                            "Content-Type:application/octet-stream\r\n" +
                            "Content-Length: 1000000\r\n" +
                            "Expect: 100-continue\r\n" +
                            "\r\n").getBytes());
                        out.flush();
                        
                        InputStream in = s.getInputStream();
                        int length;
                        byte[] buf = new byte[256*256];
                        while ((length = in.read(buf)) != -1) {
                            System.out.print(new String(buf, 0, length));
                        }
                        
                        // Console logs the 100-Continue response and then the 500-Internal server error, but ALSO the following:
                        /*
11:34:44,790 ERROR [a.s.Materializer] akka.stream.Log(akka://http-integration-spec/user/StreamSupervisor-0) - [timed] Upstream failed.
java.lang.IllegalArgumentException: requirement failed: Cannot pull port (requestParsingIn) twice
    at scala.Predef$.require(Predef.scala:224)
    at akka.stream.stage.GraphStageLogic.pull(GraphStage.scala:355)
    at akka.http.impl.engine.server.HttpServerBluePrint$ControllerStage$$anon$12$$anon$15.onPush(HttpServerBluePrint.scala:432)
                         */
                    });
                });
            });
        });
    }
}

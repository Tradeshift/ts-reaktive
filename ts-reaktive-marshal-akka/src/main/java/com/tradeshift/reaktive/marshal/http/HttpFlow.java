package com.tradeshift.reaktive.marshal.http;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Predicate;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.Done;
import akka.NotUsed;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.ContentType;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpMethod;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.Uri;
import akka.stream.Materializer;
import akka.stream.javadsl.AsPublisher;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import scala.util.Try;

public class HttpFlow {
    private static final Logger log = LoggerFactory.getLogger(HttpFlow.class);
    private static final Predicate<HttpResponse> defaultIsSuccess = r -> r.status().isSuccess();
    
    private final Http http;
    private final Materializer materializer;
    
    public HttpFlow(Http http, Materializer materializer) {
        this.http = http;
        this.materializer = materializer;
    }
    
    
    /**
     * Returns a flow that, when materializes, makes an HTTP request with the given [method] to [uri], with an entity
     * of [contentType] and optional extra [headers], using flow input as request body.
     * 
     * The response body will be the output bytes of the flow.
     * 
     * @param isSuccess predicate that decides whether a response is acceptable. If not, the stream will be failed with
     *        an IllegalStateException.
     */
    public Flow<ByteString, ByteString, CompletionStage<Done>> flow(HttpMethod method, Uri uri, ContentType contentType, Predicate<HttpResponse> isSuccess, HttpHeader... headers) {
        Sink<ByteString, Publisher<ByteString>> in = Sink.asPublisher(AsPublisher.WITHOUT_FANOUT);
        Source<ByteString, Subscriber<ByteString>> out = Source.asSubscriber();
        
        return Flow.fromSinkAndSourceMat(in, out, Keep.both()).mapMaterializedValue(pair -> {
            Source<ByteString, NotUsed> inReader = Source.fromPublisher(pair.first());
            HttpRequest rq = HttpRequest.create().withMethod(method).withUri(uri).addHeaders(Arrays.asList(headers)).withEntity(HttpEntities.createChunked(contentType, inReader));
            CompletableFuture<Done> mat = new CompletableFuture<>();
            
            http.singleRequest(rq, materializer).thenAccept(resp -> {
                if (isSuccess.test(resp)) {
                    resp.entity().getDataBytes()
                        .alsoTo(Sink.fromSubscriber(pair.second()))
                        .runWith(Sink.onComplete(t -> completeWithTry(mat, t)), materializer);
                } else {
                    log.error("Http responded error: {}", resp);
                    resp.discardEntityBytes(materializer);
                    pair.second().onError(new IllegalStateException("Unsuccessful HTTP response: " + resp.status() + " for " + rq));
                }
            }).exceptionally(x -> {
                Throwable cause = (x instanceof CompletionException) ? x.getCause() : x;
                log.error("Could not make http request", cause);
                pair.second().onError(cause);
                return null;
            });
            
            return mat;
        });
    }
    
    private static <T> void completeWithTry(CompletableFuture<T> future, Try<T> t) {
        if (t.isSuccess()) {
            future.complete(t.get());
        } else {
            future.completeExceptionally(t.failed().get());
        }
    }


    public Flow<ByteString, ByteString, CompletionStage<Done>> flow(HttpMethod method, Uri uri, ContentType contentType, HttpHeader... headers) {
        return flow(method, uri, contentType, defaultIsSuccess, headers);
    }
    
    /**
     * Returns a source that, when materialized, makes an HTTP request with the given [method] to [uri], with no request entity, and optional extra [headers].
     * 
     * The response body will be the output bytes of the source.
     * 
     * @param isSuccess predicate that decides whether a response is acceptable. If not, the stream will be failed with
     *        an IllegalStateException.
     */
    public Source<ByteString,NotUsed> source(HttpMethod method, Uri uri, Predicate<HttpResponse> isSuccess, HttpHeader... headers) {
        HttpRequest request = HttpRequest.create().withMethod(method).withUri(uri).addHeaders(Arrays.asList(headers));
        return Source.fromCompletionStage(http.singleRequest(request, materializer)).flatMapConcat(resp -> {
            if (isSuccess.test(resp)) {
                return resp.entity().getDataBytes();
            } else {
                log.error("Http responded error: {}", resp);
                resp.discardEntityBytes(materializer);
                throw new IllegalStateException("Unsuccessful HTTP response: " + resp.status() + " for " + request);
            }
        });
    }
    
    public Source<ByteString,NotUsed> source(HttpMethod method, Uri uri, HttpHeader... headers) {
        return source(method, uri, defaultIsSuccess, headers);
    }
    
}

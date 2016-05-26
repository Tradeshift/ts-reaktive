package com.tradeshift.reaktive.testkit;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import akka.actor.ActorSystem;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpEntity;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.MediaRanges;
import akka.http.javadsl.model.MediaTypes;
import akka.http.javadsl.model.headers.Accept;
import akka.stream.Materializer;
import akka.util.ByteString;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Wrapper for akka's http client to a predefined base URL, with convenience methods to put and get JSON and XML.
 */
public class TestHttpClient {
    private final ActorSystem system;
    private final Materializer materializer;
    private final String baseUrl;
    
    private long timeout = 10000;
    
    public TestHttpClient(ActorSystem system, Materializer materializer, String baseUrl) {
        this.system = system;
        this.materializer = materializer;
        this.baseUrl = baseUrl;
    }

    public void putXML(String path, String content) {
        perform(HttpRequest
            .PUT(getHttpUri(path))
            .withEntity(ContentTypes.TEXT_XML_UTF8, content.getBytes(Charset.forName("UTF-8"))));
    }
    
    public void patchJSON(String path, String content) {
        perform(HttpRequest
            .PATCH(getHttpUri(path))
            .withEntity(ContentTypes.APPLICATION_JSON, content.getBytes(Charset.forName("UTF-8"))));
    }
    
    public JsonNode getJSON(String path) {
        HttpEntity.Strict entity = perform(HttpRequest
            .GET(getHttpUri(path))
            .addHeader(Accept.create(MediaRanges.create(MediaTypes.APPLICATION_JSON))));
        if (!entity.getContentType().equals(ContentTypes.APPLICATION_JSON)) {
            throw new AssertionError("Expecting an application/json response, but got " + entity);
        }
        try {
            return new ObjectMapper().readTree(entity.getData().toArray());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    public ByteString getByteString(String path) {
        return perform(HttpRequest.GET(getHttpUri(path))).getData();
    }
    
    public HttpResponse tryGet(String path) {
        return send(HttpRequest.GET(getHttpUri(path)));
    }
    
    /**
     * Sets the timeout for outgoing HTTP requests, in milliseconds. Defaults to 10000 (10 seconds).
     */
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }
    
    private HttpEntity.Strict perform(HttpRequest request) {
        try {
            HttpResponse response = send(request);
            if (response.status().intValue() >= 400 && response.status().intValue() < 500) { 
                throw new AssertionError("Expected " + request + " to succeed, but failed with " + response);
            } else if (response.status().isFailure()) {
                throw new RuntimeException("Request " + request + " failed unexpectedly with " + response);
            }
            return response.entity().toStrict(timeout, materializer).toCompletableFuture().get(timeout, TimeUnit.MILLISECONDS);
        } catch (ExecutionException x) {
            throw (RuntimeException) x.getCause();
        } catch (InterruptedException | TimeoutException e) {
            throw new RuntimeException(e);
        }        
    }
    
    private HttpResponse send(HttpRequest request) {
        try {
            return Http.get(system)
                .singleRequest(request, materializer)
                .toCompletableFuture()
                .get(timeout, TimeUnit.MILLISECONDS);
        } catch (ExecutionException x) {
            throw (RuntimeException) x.getCause();
        } catch (InterruptedException | TimeoutException e) {
            throw new RuntimeException(e);
        }        
    }

    private String getHttpUri(String path) {
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return baseUrl + path;
    }    
}

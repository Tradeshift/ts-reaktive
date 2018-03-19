package com.tradeshift.reaktive.akka;

import static akka.http.javadsl.server.Directives.complete;
import static akka.http.javadsl.server.Directives.parameter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.cuppa.Cuppa.afterEach;
import static org.forgerock.cuppa.Cuppa.beforeEach;
import static org.forgerock.cuppa.Cuppa.describe;
import static org.forgerock.cuppa.Cuppa.it;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.forgerock.cuppa.junit.CuppaRunner;
import org.junit.runner.RunWith;

import com.tradeshift.reaktive.testkit.SharedActorSystemSpec;
import com.tradeshift.reaktive.throttle.ThrottleDirective;
import com.typesafe.config.ConfigFactory;

import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.Route;

@RunWith(CuppaRunner.class)
public class ThrottleDirectiveSpec extends SharedActorSystemSpec {
    private final Http http = Http.get(system);
    
    private ServerBinding server;

    public ThrottleDirectiveSpec() {
        super(ConfigFactory.parseResources("ThrottleDirectiveSpec.conf"));
    }
    
    private HttpResponse makeRequest(String user) {
        HttpResponse response;
        try {
            response = http.singleRequest(HttpRequest.GET("http://localhost:8173?user=" + user)).toCompletableFuture().get(2, TimeUnit.SECONDS);
            response.discardEntityBytes(materializer);
            return response;
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException(e);
        }
    }
    
    {
        describe("ThrottleDirective.throttle", () -> {
            beforeEach(() -> {
                ThrottleDirective throttler = new ThrottleDirective(system, 3, Duration.of(2, ChronoUnit.SECONDS), 5);
                Route route = parameter("user", user ->
                    throttler.throttleSealed(user, () ->
                        complete("OK")
                    )
                );
                server = http.bindAndHandle(route.flow(system, materializer), ConnectHttp.toHost("127.0.0.1:8173"), materializer)
                    .toCompletableFuture()
                    .get(2, TimeUnit.SECONDS);
            });
            
            afterEach(() -> server.unbind().toCompletableFuture().get(2, TimeUnit.SECONDS));
            
            it("should fail requests after all tokens are used up, but allow them again after a delay", () -> {
                // Make 5 quick requests to use up all tokens
                for (int i = 0; i < 5; i++) {
                    HttpResponse response = makeRequest("justme");
                    assertThat(response.status()).isEqualTo(StatusCodes.OK);
                }
                HttpResponse response = makeRequest("justme");
                assertThat(response.status()).isEqualTo(StatusCodes.ENHANCE_YOUR_CALM);
                
                Thread.sleep(2500);
                
                response = makeRequest("justme");
                assertThat(response.status()).isEqualTo(StatusCodes.OK);
            });
            
            it("should not throttle requests for another user", () -> {
                // Make 5 quick requests to use up all tokens
                for (int i = 0; i < 5; i++) {
                    makeRequest("one");
                }
                // Make a request as another user
                HttpResponse response = makeRequest("two");
                assertThat(response.status()).isEqualTo(StatusCodes.OK);
            });
        });
    }
}

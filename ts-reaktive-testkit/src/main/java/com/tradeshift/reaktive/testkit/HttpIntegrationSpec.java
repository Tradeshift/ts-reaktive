package com.tradeshift.reaktive.testkit;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.server.Route;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;

/**
 * Base class for tests that test the HTTP layer against mocked service classes, but by making actual HTTP requests
 * rather than using akka's RouteTest infrastructure. Making real HTTP requests allows for testing more real-world
 * error scenarios.
 */
public abstract class HttpIntegrationSpec {
    public static final Config config = ConfigFactory.defaultReference();
    public static final ActorSystem system = ActorSystem.create("http-integration-spec", config);
    public static final Materializer materializer = ActorMaterializer.create(system);

    /**
     * Starts a real akka http server for the given route, and then runs the given consumer. After the consumer returns,
     * the server is automatically stopped (also in case of exceptions / assertion failures).
     * 
     * The consumer should use the given {@link TestHttpClient} to make requests against the route. It's automatically
     * configured to contact the http server on the right port.
     */
    public static void serve(Route route, Consumer<TestHttpClient> checks) {
        final int port = randomPort();
        try {
            ServerBinding binding = Http.get(system).bindAndHandle(route.flow(system, materializer), ConnectHttp.toHost("localhost", port), materializer).toCompletableFuture().get(10, TimeUnit.SECONDS);
            try {
                checks.accept(new TestHttpClient(system, materializer, "http://localhost:" + port));
            } finally {
                binding.unbind().toCompletableFuture().get(10, TimeUnit.SECONDS);
            }            
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException(e);
        }
    }
    
    private static int randomPort() {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();    // returns the port the system selected
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

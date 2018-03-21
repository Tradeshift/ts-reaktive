package com.tradeshift.reaktive.replication.io;

import static akka.http.javadsl.server.Directives.handleWebSocketMessages;
import static akka.http.javadsl.server.Directives.path;
import static akka.http.javadsl.server.Directives.pathPrefix;
import static akka.http.javadsl.server.Directives.route;
import static akka.pattern.PatternsCS.ask;

import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tradeshift.reaktive.akka.SharedActorMaterializer;
import com.tradeshift.reaktive.protobuf.Query;
import com.tradeshift.reaktive.protobuf.ReplicationMessages.EventsPersisted;
import com.tradeshift.reaktive.ssl.SSLFactory;
import com.typesafe.config.Config;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.ConnectHttpsImpl;
import akka.http.javadsl.ConnectionContext;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.ws.BinaryMessage;
import akka.http.javadsl.model.ws.Message;
import akka.http.javadsl.server.Route;
import akka.stream.ActorMaterializer;
import akka.stream.TLSClientAuth;
import akka.stream.javadsl.Flow;
import akka.util.ByteString;
import akka.util.Timeout;
import io.vavr.collection.Map;
import io.vavr.control.Option;

/**
 * Server that allows a data center to receive incoming web socket connections from a {@link WebSocketDataCenterClient}, in order
 * to receive incoming replicated events.
 */
public class WebSocketDataCenterServer {
    private static final Logger log = LoggerFactory.getLogger(WebSocketDataCenterServer.class);
    
    private final Timeout timeout;
    private final int maxInFlight;

    private CompletionStage<ServerBinding> binding;
    
    /**
     * Creates the web socket server and binds to the port, according to [config].
     */
    public WebSocketDataCenterServer(ActorSystem system, Map<String,ActorRef> tagsAndShardRegions) {
        Config config = system.settings().config().getConfig("ts-reaktive.replication.server");
        ActorMaterializer materializer = SharedActorMaterializer.get(system);
        this.timeout = Timeout.apply(config.getDuration("timeout", TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS);
        this.maxInFlight = config.getInt("max-in-flight");
        Route route = pathPrefix("events", () -> route(
            tagsAndShardRegions.map(t ->
                path(t._1, () ->
                    handleWebSocketMessages(flow(t._2))
                )
            ).toJavaArray(Route.class)
        ));
        ConnectHttp httpOpts = SSLFactory.createSSLContext(config).map(sslContext ->
            (ConnectHttp) new ConnectHttpsImpl(config.getString("host"), config.getInt("port"), Optional.of(
                ConnectionContext.https(sslContext, Optional.empty(), Optional.empty(), Optional.of(TLSClientAuth.need()), Optional.empty())
            ))
        ).getOrElse(() ->
            ConnectHttp.toHost(config.getString("host"), config.getInt("port"))
        );
        this.binding = Http.get(system).bindAndHandle(route.flow(system, materializer), httpOpts, materializer);
    }
    
    /**
     * Returns the server binding for this websocket server.
     */
    public CompletionStage<ServerBinding> getBinding() {
        return binding;
    }

    private Flow<Message,Message,?> flow(ActorRef shardRegion) {
        return Flow.<Message>create()
            .map(msg -> {
                if (msg.isText()) {
                    log.warn("Ignoring unexpected text-kind web socket message {}", msg);
                    return Option.<Query.EventEnvelope>none();
                } else {
                    return Option.<Query.EventEnvelope>some(Query.EventEnvelope.parseFrom(msg.asBinaryMessage().getStrictData().toArray()));
                }
            })
            .filter(o -> o.isDefined())
            .map(o -> o.get())
            .mapAsync(maxInFlight, e -> ask(shardRegion, e, timeout))
            .map(resp -> (Long) resp)
            .map(l -> BinaryMessage.create(ByteString.fromArray(EventsPersisted.newBuilder().setOffset(l).build().toByteArray())));
    }
}

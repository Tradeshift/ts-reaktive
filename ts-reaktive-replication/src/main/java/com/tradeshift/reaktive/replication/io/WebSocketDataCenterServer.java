package com.tradeshift.reaktive.replication.io;

import static akka.pattern.PatternsCS.ask;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tradeshift.reaktive.protobuf.ReplicationMessages.EventsPersisted;
import com.tradeshift.reaktive.protobuf.Query;
import com.typesafe.config.Config;

import akka.actor.ActorRef;
import akka.http.javadsl.model.ws.BinaryMessage;
import akka.http.javadsl.model.ws.Message;
import akka.http.javadsl.server.Directives;
import akka.http.javadsl.server.Route;
import akka.stream.javadsl.Flow;
import akka.util.ByteString;
import akka.util.Timeout;
import javaslang.control.Option;

/**
 * Server that allows a data center to receive incoming web socket connections from a {@link WebSocketDataCenterClient}, in order
 * to receive incoming replicated events.
 */
public class WebSocketDataCenterServer {
    private static final Logger log = LoggerFactory.getLogger(WebSocketDataCenterServer.class);
    
    private ActorRef shardRegion;
    private final Timeout timeout;
    private final int maxInFlight;
    
    public WebSocketDataCenterServer(Config config, ActorRef shardRegion) {
        this.shardRegion = shardRegion;
        this.timeout = Timeout.apply(config.getDuration("timeout", TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS);
        this.maxInFlight = config.getInt("max-in-flight");
    }

    public Route route() {
        return Directives.handleWebSocketMessages(flow());
    }

    private Flow<Message,Message,?> flow() {
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

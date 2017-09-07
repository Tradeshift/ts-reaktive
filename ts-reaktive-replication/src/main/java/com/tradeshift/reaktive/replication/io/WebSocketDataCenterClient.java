package com.tradeshift.reaktive.replication.io;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tradeshift.reaktive.protobuf.ReplicationMessages.EventsPersisted;
import com.tradeshift.reaktive.protobuf.EventEnvelopeSerializer;
import com.tradeshift.reaktive.replication.DataCenter;

import akka.actor.ActorSystem;
import akka.http.javadsl.ConnectionContext;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.ws.BinaryMessage;
import akka.http.javadsl.model.ws.Message;
import akka.http.javadsl.model.ws.WebSocketRequest;
import akka.http.javadsl.settings.ClientConnectionSettings;
import akka.persistence.query.EventEnvelope;
import akka.stream.javadsl.Flow;
import akka.util.ByteString;

/**
 * A client that can stream events into a datacenter that has exposed its event receiving API using WebSocketDataCenterServer.
 */
public class WebSocketDataCenterClient implements DataCenter {
    private static final Logger log = LoggerFactory.getLogger(WebSocketDataCenterClient.class);
    
    private final ActorSystem system;
    private final String uri;
    private final ConnectionContext connectionContext;
    private final String name;
    private final EventEnvelopeSerializer serializer;
    
    /**
     * Creates a new WebSocketDataCenterClient
     * @param name Name of the data center
     * @param connectionContext Connection context to apply. Any SSL client certificate should be configured here.
     * @param uri Target URL that the remote datacenter is listening on.
     */
    public WebSocketDataCenterClient(ActorSystem system, ConnectionContext connectionContext, String name, String uri, EventEnvelopeSerializer serializer) {
        this.system = system;
        this.connectionContext = connectionContext;
        this.name = name;
        this.uri = uri;
        this.serializer = serializer;
    }

    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public Flow<EventEnvelope,Long,?> uploadFlow() {
        ClientConnectionSettings settings = ClientConnectionSettings.create(system.settings().config());
        
        return Flow.<EventEnvelope>create()
            .map(e -> (Message) BinaryMessage.create(serialize(e)))
            .via(Http.get(system).webSocketClientFlow(WebSocketRequest.create(uri), connectionContext, Optional.empty(), settings, system.log()))
            .map(msg -> {
                if (msg.isText()) {
                    log.warn("Ignoring unexpected text-type WS message {}", msg);
                    return 0l;
                } else {
                    EventsPersisted applied = EventsPersisted.parseFrom(
                        msg.asBinaryMessage().getStrictData().iterator().asInputStream());
                    return applied.hasOffset() ? applied.getOffset() : 0l;
                }})
            .filter(l -> l > 0);
    }

    protected ByteString serialize(EventEnvelope e) {
        return ByteString.fromArray(serializer.toProtobuf(e).toByteArray());
    }
}

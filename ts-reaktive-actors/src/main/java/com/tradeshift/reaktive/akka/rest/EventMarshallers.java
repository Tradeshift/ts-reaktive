package com.tradeshift.reaktive.akka.rest;

import java.io.IOException;
import java.util.function.Function;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import com.tradeshift.reaktive.protobuf.Query;

import akka.actor.ActorSystem;
import akka.http.javadsl.marshalling.Marshaller;
import akka.http.javadsl.model.ContentType;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.MediaType;
import akka.http.javadsl.model.MediaTypes;
import akka.persistence.query.EventEnvelope;
import akka.persistence.query.Sequence;
import akka.persistence.query.TimeBasedUUID;
import akka.serialization.Serialization;
import akka.serialization.SerializationExtension;
import akka.stream.javadsl.Source;
import akka.util.ByteStringBuilder;
import io.vavr.collection.HashMap;

/**
 * Serializes instances of {@link akka.persistence.query.EventEnvelope},
 * into the protobuf {@link EventEnvelope}. In order to do this, it reuses akka's serialization mechanism.
 */
public class EventMarshallers {
    public static final MediaType.Binary applicationProtobufDelimited =
            MediaTypes.customBinary("application", "protobuf", true,
                HashMap.of("delimited", "true").put("messageType", "Query.EventEnvelope").toJavaMap(),
                true);
        
    /**
     * Returns an akka ByteString with the given protobuf message as delimited protobuf.
     */
    public static akka.util.ByteString serializeDelimited(MessageLite protobufMessage) {
        final ByteStringBuilder out = new ByteStringBuilder();
        try {
            protobufMessage.writeDelimitedTo(out.asOutputStream());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return out.result();
    }
    
    /** 
     * Returns a Marshaller that marshals event envelopes using the given function, writing the resulting protobuf messages as delimited protobuf
     * into a chunked HTTP response with the given content type. 
     */
    public static Marshaller<Source<EventEnvelope, ?>, HttpResponse> marshallerWith(Function<EventEnvelope, ? extends MessageLite> f, ContentType contentType) {
        return Marshaller.<Source<EventEnvelope, ?>, HttpResponse>opaque(events -> 
            HttpResponse.create().withEntity(
                HttpEntities.create(
                    contentType, events.map(e -> serializeDelimited(f.apply(e)))
                )
            )
        );
    }    

    /** 
     * Returns a Marshaller that marshals event envelopes using the given function, writing the resulting protobuf messages as delimited protobuf
     * into a chunked HTTP response with a generic protobuf content type. 
     */
    public static Marshaller<Source<EventEnvelope, ?>, HttpResponse> marshallerWith(Function<EventEnvelope, ? extends MessageLite> f) {
        return marshallerWith(f, applicationProtobufDelimited.toContentType());
    }    
    
    /** 
     * Returns a Marshaller that marshals event envelopes using the default akka serialization as delimited protobuf, into a chunked HTTP response
     * with a generic protobuf content type.
     */
    public static Marshaller<Source<EventEnvelope, ?>, HttpResponse> marshallerWithAkkaSerialization(ActorSystem system) {
        return marshallerWith(getAkkaSerializer(system), applicationProtobufDelimited.toContentType());
    }
    
    /**
     * Returns a function that turns persistence query EventEnvelopes into generic protobuf Query.EventEnvelope messages
     * using default akka serialization.
     */
    public static Function<EventEnvelope, Query.EventEnvelope> getAkkaSerializer(ActorSystem system) {
        Serialization ext = SerializationExtension.get(system);
        return e -> {
            ByteString event;
            if (e.event() instanceof ByteString) {
                event = (ByteString) e.event();
            } else if (e.event() instanceof byte[]) {
                event = ByteString.copyFrom((byte[]) e.event());
            } else {
                event = ByteString.copyFrom(ext.serialize(e.event()).get());
            }
            
            long timestamp = getOffsetAsEpoch(e);
                
            return Query.EventEnvelope.newBuilder()
                .setPersistenceId(e.persistenceId())
                .setTimestamp(timestamp)
                .setSequenceNr(e.sequenceNr())
                .setEvent(event)
                .build();            
        };
    }

    public static long getOffsetAsEpoch(EventEnvelope e) {
        return (e.offset() instanceof Sequence) 
            ? Sequence.class.cast(e.offset()).value()
            : com.tradeshift.reaktive.akka.UUIDs.unixTimestamp(TimeBasedUUID.class.cast(e.offset()).value());
    }
}

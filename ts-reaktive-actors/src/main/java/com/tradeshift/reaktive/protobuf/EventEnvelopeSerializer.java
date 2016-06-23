package com.tradeshift.reaktive.protobuf;

import com.google.protobuf.ByteString;

import akka.actor.ActorSystem;
import akka.persistence.query.EventEnvelope;
import akka.serialization.Serialization;
import akka.serialization.SerializationExtension;

/**
 * Serializes and deserializes instances of {@link akka.persistence.query.EventEnvelope}, 
 * into the protobuf {@link EventEnvelope}. In order to do this, it has to be provided with a known
 * type of the events it deserializes.
 */
public class EventEnvelopeSerializer {
    private final Serialization ext;

    /**
     * Creates a new EventEnvelopeSerializer.
     */
    public EventEnvelopeSerializer(ActorSystem system) {
        this.ext = SerializationExtension.get(system);
    }

    public Query.EventEnvelope toProtobuf(EventEnvelope e) {
        ByteString event;
        if (e.event() instanceof ByteString) {
            event = (ByteString) e.event();
        } else if (e.event() instanceof byte[]) {
            event = ByteString.copyFrom((byte[]) e.event());
        } else {
            event = ByteString.copyFrom(ext.serialize(e.event()).get());
        }
        
        return Query.EventEnvelope.newBuilder()
            .setPersistenceId(e.persistenceId())
            .setOffset(e.offset())
            .setSequenceNr(e.sequenceNr())
            .setEvent(event)
            .build();
    }
    
    /**
     * Returns an akka EventEnvelope for the given protobuf message, deserializing the event itself as well
     * (using akka serialization)
     * 
     * @param eventType Target type for the deserialized event that goes into the event field of the envelope
     */
    public EventEnvelope toAkka(Query.EventEnvelope e, Class<?> eventType) {
        Object event = ext.deserialize(e.getEvent().toByteArray(), eventType);
        return EventEnvelope.apply(e.getOffset(), e.getPersistenceId(), e.getSequenceNr(), event);
    }
    
    /**
     * Returns an akka EventEnvelope for the given protobuf message, but keeping the event itself serialized
     * as a {@link com.google.protobuf.ByteString}
     */
    public EventEnvelope toAkkaAsByteString(Query.EventEnvelope e) {
        return EventEnvelope.apply(e.getOffset(), e.getPersistenceId(), e.getSequenceNr(), e.getEvent());
    }
}

package com.tradeshift.reaktive.protobuf;

import com.google.protobuf.ByteString;

import akka.actor.ActorSystem;
import akka.persistence.query.EventEnvelope2;
import akka.persistence.query.TimeBasedUUID;
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

    public Query.EventEnvelope toProtobuf(EventEnvelope2 e) {
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
            .setTimestamp(com.tradeshift.reaktive.akka.UUIDs.unixTimestamp(TimeBasedUUID.class.cast(e.offset()).value()))
            .setSequenceNr(e.sequenceNr())
            .setEvent(event)
            .build();
    }
}

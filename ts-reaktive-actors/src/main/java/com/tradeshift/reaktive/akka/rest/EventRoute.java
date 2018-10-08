package com.tradeshift.reaktive.akka.rest;


import static akka.http.javadsl.server.Directives.complete;
import static akka.http.javadsl.server.Directives.get;
import static akka.http.javadsl.server.Directives.onSuccess;
import static akka.http.javadsl.server.Directives.parameterOptional;
import static com.tradeshift.reaktive.akka.AkkaStreams.awaitOne;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

import com.tradeshift.reaktive.akka.UUIDs;
import com.tradeshift.reaktive.protobuf.EventEnvelopeSerializer;

import akka.NotUsed;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.MediaType;
import akka.http.javadsl.model.MediaTypes;
import akka.http.javadsl.server.Route;
import akka.persistence.query.EventEnvelope;
import akka.persistence.query.NoOffset;
import akka.persistence.query.Offset;
import akka.persistence.query.TimeBasedUUID;
import akka.persistence.query.javadsl.EventsByTagQuery;
import akka.stream.Materializer;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import io.vavr.collection.HashMap;

/**
 * Exposes the full event stream of an akka persistence journal as an HTTP stream in chunked encoding,
 * by querying the journal with a fixed tag.
 *
 * Subclasses can consider overriding {@link #serialize(EventEnvelope)} if they want to provide a different
 * serialization than the default protobuf EventEnvelope representation.
 */
public class EventRoute {
    private static final MediaType.Binary mediaType =
        MediaTypes.customBinary("application", "protobuf", true,
            HashMap.of("delimited", "true").put("messageType", "Query.EventEnvelope").toJavaMap(),
            true);

    private final EventsByTagQuery journal;
    private final String tagName;
    private final Materializer materializer;
    private final EventEnvelopeSerializer serializer;

    /**
     * Creates a new EventRoute
     * @param journal The cassandra journal to read from
     * @param tagName The tag name of the events that this route should query
     */
    public EventRoute(Materializer materializer, EventsByTagQuery journal, EventEnvelopeSerializer serializer, String tagName) {
        this.materializer = materializer;
        this.journal = journal;
        this.tagName = tagName;
        this.serializer = serializer;
    }

    public Route apply() {
        return parameterOptional("since", since ->
            get(() ->
                onSuccess(() -> getEventsResponse(getEvents(since)), response ->
                    complete(response)
                )
            )
        );
    }

    protected static long toUTCTimestamp(String since) {
        try {
            return Long.parseLong(since);
        } catch (NumberFormatException x) {
            if (since.length() <= 4 || !isNumeric(since)) {
                try {
                    // Source: http://stackoverflow.com/questions/2201925/converting-iso-8601-compliant-string-to-java-util-date
                    return javax.xml.bind.DatatypeConverter.parseDateTime(since).toInstant().toEpochMilli();
                } catch (Exception y) {
                    throw new IllegalArgumentException ("Timestamp must either be in epoch millisconds or ISO-8601");
                }
            } else {
                throw new IllegalArgumentException("Timestamp epoch milliseconds out of range");
            }
        }
    }

    protected CompletionStage<HttpResponse> getEventsResponse(Source<EventEnvelope, NotUsed> events) {
        return awaitOne(events, materializer).thenApply(source ->
            HttpResponse.create()
                .withEntity(HttpEntities.createChunked(mediaType.toContentType(), source.map(this::serialize)))
        );
    }

    private static boolean isNumeric(String since) {
        for (int i = 0; i < since.length(); i++) {
            if (i == 0) {
                if (since.charAt(i) != '-' && !Character.isDigit(since.charAt(i))) return false;
            } else {
                if (!Character.isDigit(since.charAt(i))) return false;
            }
        }
        return true;
    }

    public Source<EventEnvelope,NotUsed> getEvents(Optional<String> since) {
        return journal.eventsByTag(tagName, timeBasedUUIDFrom(since.map(EventRoute::toUTCTimestamp).orElse(0L)));
    }

    protected Offset timeBasedUUIDFrom(long timestamp) {
        return (timestamp == 0) ? NoOffset.getInstance() : new TimeBasedUUID(UUIDs.startOf(timestamp));
    }

    /**
     * Serializes the given akka event envelope into actual bytes that will become an HTTP chunk in the response.
     */
    protected ByteString serialize(EventEnvelope envelope) {
        return ByteString.fromArray(serializer.toProtobuf(envelope).toByteArray());
    }
}

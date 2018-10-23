package com.tradeshift.reaktive.akka.rest;


import static akka.http.javadsl.server.Directives.complete;
import static akka.http.javadsl.server.Directives.get;
import static akka.http.javadsl.server.Directives.onSuccess;
import static akka.http.javadsl.server.Directives.parameterOptional;
import static com.tradeshift.reaktive.akka.AkkaStreams.awaitOne;

import java.time.Instant;
import java.util.Optional;

import com.tradeshift.reaktive.akka.UUIDs;

import akka.NotUsed;
import akka.http.javadsl.marshalling.Marshaller;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.unmarshalling.Unmarshaller;
import akka.persistence.query.EventEnvelope;
import akka.persistence.query.NoOffset;
import akka.persistence.query.Offset;
import akka.persistence.query.TimeBasedUUID;
import akka.persistence.query.javadsl.EventsByTagQuery;
import akka.stream.Materializer;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Source;

/**
 * Exposes the full event stream of an akka persistence journal as an HTTP stream in chunked encoding,
 * by querying the journal with a fixed tag.
 *
 * Subclasses can consider overriding {@link #serialize(EventEnvelope)} if they want to provide a different
 * serialization than the default protobuf EventEnvelope representation.
 */
public class EventRoute {
    public static final Unmarshaller<String,Instant> INSTANT = Unmarshaller.sync(EventRoute::toInstant);

    public static Instant toInstant(String since) {
        try {
            try {
                return Instant.ofEpochMilli(Long.parseLong(since));
            } catch (NumberFormatException x) {
                return Instant.parse(since);
            }
        } catch (Exception x) {
            throw new IllegalArgumentException("Timestamp must either be in epoch millisconds or ISO-8601");
        }
    }
    
    private final EventsByTagQuery journal;
    private final String tagName;
    private final Materializer materializer;
    private final Marshaller<Source<EventEnvelope, ?>, HttpResponse> marshaller;

    /**
     * Creates a new EventRoute
     * @param journal The cassandra journal to read from
     * @param tagName The tag name of the events that this route should query
     */
    public EventRoute(Materializer materializer, EventsByTagQuery journal, Marshaller<Source<EventEnvelope, ?>, HttpResponse> marshaller, String tagName) {
        this.materializer = materializer;
        this.journal = journal;
        this.tagName = tagName;
        this.marshaller = marshaller;
    }

    /**
     * Returns a route that serves the event resource unfiltered, i.e. returning all events
     */
    public Route apply() {
        return apply(Flow.create());
    }
    
    /**
     * Returns a route that serves the event resource while passing all events through the given filter,
     * which can implement security restrictions.
     */
    public Route apply(Flow<EventEnvelope, EventEnvelope, ?> filter) {
        return parameterOptional(INSTANT, "since", since ->
            get(() ->
                onSuccess(() -> awaitOne(getEvents(since, filter), materializer), events ->
                    complete(events, marshaller)
                )
            )
        );
    }

    public Source<EventEnvelope,NotUsed> getEvents(Optional<Instant> since, Flow<EventEnvelope, EventEnvelope, ?> filter) {
        return journal
            .eventsByTag(tagName, timeBasedUUIDFrom(since.map(t -> t.toEpochMilli()).orElseGet(() -> 0l)))
            .via(filter);
    }

    public static Offset timeBasedUUIDFrom(long timestamp) {
        return (timestamp == 0) ? NoOffset.getInstance() : new TimeBasedUUID(UUIDs.startOf(timestamp));
    }
}

package com.tradeshift.reaktive.materialize;

import java.util.HashMap;
import java.util.Map;

import kamon.Kamon;
import kamon.metric.Counter;
import kamon.metric.Gauge;
import kamon.metric.Histogram;
import kamon.metric.MeasurementUnit;
import kamon.tag.TagSet;

public class MaterializerMetrics {
    private final Map<String, Object> baseTags;
    private final Counter events;
    private final Counter restarts;
    private final Gauge reimportRemaining;
    /** The current timestamp for each worker, in milliseconds since the epoch */
    private final Gauge offset;
    /** The delay between the timestamp of each worker and now(), in milliseconds */
    private final Gauge delay;
    /** The time remaining for each worker, in milliseconds (if there is an end timestamp) */
    private final Gauge remaining;
    /** The duration, milliseconds, of materializing a single event */
    private final Histogram materializationDuration;
    private final Gauge workers;
    private final Gauge streams;

    public MaterializerMetrics(String name, io.vavr.collection.Map<String, String> additionalTags) {
        additionalTags.put("journal-materializer", name);
        baseTags = new HashMap<>(additionalTags.toJavaMap());
        TagSet tagSet = TagSet.from(baseTags);
        this.events = Kamon.counter("journal-materializer.events").withoutTags();
        this.restarts = Kamon.counter("journal-materializer.restarts").withTags(tagSet);
        this.reimportRemaining = Kamon.gauge("journal-materializer.reimport-remaining", MeasurementUnit.time().milliseconds()).withTags(tagSet);
        this.offset = Kamon.gauge("journal-materializer.offset", MeasurementUnit.time().milliseconds()).withoutTags();
        this.delay = Kamon.gauge("journal-materializer.delay", MeasurementUnit.time().milliseconds()).withoutTags();
        this.remaining = Kamon.gauge("journal-materializer.remaining", MeasurementUnit.time().milliseconds()).withoutTags();
        this.materializationDuration = Kamon.histogram("journal-materializer.materialization-duration", MeasurementUnit.time().milliseconds()).withoutTags();
        this.workers = Kamon.gauge("journal-materializer.workers").withTags(tagSet);
        this.streams = Kamon.gauge("journal-materializer.streams").withTags(tagSet);
    }

    public Counter getEvents(int index) {
        return events.withTags(TagSet.from(baseTags).withTag("index", String.valueOf(index)));
    }

    public Counter getRestarts() {
        return restarts;
    }

    public Gauge getOffset(int index) {
        return offset.withTags(TagSet.from(baseTags).withTag("index", String.valueOf(index)));
    }

    public Gauge getDelay(int index) {
        return delay.withTags(TagSet.from(baseTags).withTag("index", String.valueOf(index)));
    }

    public Gauge getRemaining(int index) {
        return remaining.withTags(TagSet.from(baseTags).withTag("index", String.valueOf(index)));
    }

    public Histogram getMaterializationDuration(int index) {
        return materializationDuration.withTags(TagSet.from(baseTags).withTag("index", String.valueOf(index)));
    }

    public Gauge getReimportRemaining() {
        return reimportRemaining;
    }

    public Gauge getWorkers() {
        return workers;
    }

    public Gauge getStreams() {
        return streams;
    }
}

package com.tradeshift.reaktive.materialize;

import io.vavr.collection.Map;
import kamon.Kamon;
import kamon.metric.Counter;
import kamon.metric.CounterMetric;
import kamon.metric.Gauge;
import kamon.metric.GaugeMetric;
import kamon.metric.Histogram;
import kamon.metric.HistogramMetric;
import kamon.metric.MeasurementUnit;

public class MaterializerMetrics {
    private final Map<String, String> baseTags;
    private final CounterMetric events;
    private final Counter restarts;
    private final Gauge reimportRemaining;
    /** The current timestamp for each worker, in milliseconds since the epoch */
    private final GaugeMetric offset;
    /** The delay between the timestamp of each worker and now(), in milliseconds */
    private final GaugeMetric delay;
    /** The time remaining for each worker, in milliseconds (if there is an end timestamp) */
    private final GaugeMetric remaining;
    /** The duration, milliseconds, of materializing a single event */
    private final HistogramMetric materializationDuration;
    private final Gauge workers;
    private final Gauge streams;

    public MaterializerMetrics(String name, Map<String, String> additionalTags) {
        baseTags = additionalTags.put("journal-materializer", name);
        java.util.Map<String, String> tags = baseTags.toJavaMap();
        this.events = Kamon.counter("journal-materializer.events");
        this.restarts = Kamon.counter("journal-materializer.restarts").refine(tags);
        this.reimportRemaining = Kamon.gauge("journal-materializer.reimport-remaining", MeasurementUnit.time().milliseconds()).refine(tags);
        this.offset = Kamon.gauge("journal-materializer.offset", MeasurementUnit.time().milliseconds());
        this.delay = Kamon.gauge("journal-materializer.delay", MeasurementUnit.time().milliseconds());
        this.remaining = Kamon.gauge("journal-materializer.remaining", MeasurementUnit.time().milliseconds());
        this.materializationDuration = Kamon.histogram("journal-materializer.materialization-duration", MeasurementUnit.time().milliseconds());
        this.workers = Kamon.gauge("journal-materializer.workers").refine(tags);
        this.streams = Kamon.gauge("journal-materializer.streams").refine(tags);
    }

    public Counter getEvents(int index) {
        return events.refine(baseTags.put("index", String.valueOf(index)).toJavaMap());
    }

    public Counter getRestarts() {
        return restarts;
    }

    public Gauge getOffset(int index) {
        return offset.refine(baseTags.put("index", String.valueOf(index)).toJavaMap());
    }

    public Gauge getDelay(int index) {
        return delay.refine(baseTags.put("index", String.valueOf(index)).toJavaMap());
    }

    public Gauge getRemaining(int index) {
        return remaining.refine(baseTags.put("index", String.valueOf(index)).toJavaMap());
    }

    public Histogram getMaterializationDuration(int index) {
        return materializationDuration.refine(baseTags.put("index", String.valueOf(index)).toJavaMap());
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

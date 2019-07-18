package com.tradeshift.reaktive.materialize;

import java.util.Map;

import io.vavr.collection.HashMap;
import kamon.Kamon;
import kamon.metric.Counter;
import kamon.metric.CounterMetric;
import kamon.metric.Gauge;
import kamon.metric.GaugeMetric;
import kamon.metric.MeasurementUnit;

public class MaterializerMetrics {
    private final HashMap<String, String> baseTags;
    private final CounterMetric events;
    private final Counter restarts;
    private final Gauge reimportRemaining;
    /** The current timestamp for each worker, in milliseconds since the epoch */
    private final GaugeMetric offset;
    /** The delay between the timestamp of each worker and now(), in milliseconds */
    private final GaugeMetric delay;
    /** The rime remaining for each worker, in milliseconds (if there is an end timestamp) */
    private final GaugeMetric remaining;

    public MaterializerMetrics(String name) {
        baseTags = HashMap.of("journal-materializer", name);
        Map<String, String> tags = baseTags.toJavaMap();
        this.events = Kamon.counter("journal-materializer.events");
        this.restarts = Kamon.counter("journal-materializer.restarts").refine(tags);
        this.reimportRemaining = Kamon.gauge("journal-materializer.reimport-remaining", MeasurementUnit.time().milliseconds()).refine(tags);
        this.offset = Kamon.gauge("journal-materializer.offset", MeasurementUnit.time().milliseconds());
        this.delay = Kamon.gauge("journal-materializer.delay", MeasurementUnit.time().milliseconds());
        this.remaining = Kamon.gauge("journal-materializer.remaining", MeasurementUnit.time().milliseconds());
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

    public Gauge getReimportRemaining() {
        return reimportRemaining;
    }
}

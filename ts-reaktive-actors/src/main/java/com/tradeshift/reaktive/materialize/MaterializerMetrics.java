package com.tradeshift.reaktive.materialize;

import java.util.Map;

import io.vavr.collection.HashMap;
import kamon.Kamon;
import kamon.metric.Counter;
import kamon.metric.Histogram;
import kamon.metric.MeasurementUnit;

public class MaterializerMetrics {

    public MaterializerMetrics(String name) {
        Map<String, String> tags = HashMap.of("journal-materializer", name).toJavaMap();
        this.events = Kamon.counter("journal-materializer.events").refine(tags);
        this.restarts = Kamon.counter("journal-materializer.restarts").refine(tags);
        this.reimportRemaining = Kamon.histogram("journal-materializer.reimport-remaining", MeasurementUnit.time().milliseconds()).refine(tags);
        this.offset = Kamon.histogram("journal-materializer.offset",
            MeasurementUnit.time().milliseconds()).refine(tags);
    }

    private final Counter events;
    private final Counter restarts;
    private final Histogram offset;
    private final Histogram reimportRemaining;

    public Counter getEvents() {
        return events;
    }

    public Counter getRestarts() {
        return restarts;
    }

    public Histogram getOffset() {
        return offset;
    }

    public Histogram getReimportRemaining() {
        return reimportRemaining;
    }
}

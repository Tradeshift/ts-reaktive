package com.tradeshift.reaktive.materialize;

import java.util.Map;

import io.vavr.collection.HashMap;
import kamon.Kamon;
import kamon.metric.Counter;
import kamon.metric.Histogram;
import kamon.metric.HistogramMetric;
import kamon.metric.MeasurementUnit;

public class MaterializerMetrics {
    private final HashMap<String, String> baseTags;
    private final Counter events;
    private final Counter restarts;
    private final Histogram reimportRemaining;
    private final HistogramMetric offset;

    public MaterializerMetrics(String name) {
        baseTags = HashMap.of("journal-materializer", name);
        Map<String, String> tags = baseTags.toJavaMap();
        this.events = Kamon.counter("journal-materializer.events").refine(tags);
        this.restarts = Kamon.counter("journal-materializer.restarts").refine(tags);
        this.reimportRemaining = Kamon.histogram("journal-materializer.reimport-remaining", MeasurementUnit.time().milliseconds()).refine(tags);
        this.offset = Kamon.histogram("journal-materializer.offset",
            MeasurementUnit.time().milliseconds());
    }


    public Counter getEvents() {
        return events;
    }

    public Counter getRestarts() {
        return restarts;
    }

    public Histogram getOffset(int index) {
        return offset.refine(baseTags.put("index", String.valueOf(index)).toJavaMap());
    }

    public Histogram getReimportRemaining() {
        return reimportRemaining;
    }
}

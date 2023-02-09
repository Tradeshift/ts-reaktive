package com.tradeshift.reaktive.cassandra;

import java.util.concurrent.TimeUnit;

import akka.actor.ActorSystem;

import akka.stream.alpakka.cassandra.CassandraMetricsRegistry;
import com.codahale.metrics.MetricRegistry;
import com.readytalk.metrics.StatsDReporter;

/**
 * Cassandra's Java client has its own monitoring using codahale metrics. This class can route those 
 * metrics into statsd, so that they go together with the other metrics what Kamon is collecting.
 * 
 * This relies on akka-persistence-cassandra's ability to expose the cassandra metrics
 * using {@link akka.persistence.cassandra.CassandraMetricsRegistry}.
 */
public class CassandraLogger {
    /**
     * Starts logging the metrics for cassandra into statsd. 
     */
    public static void apply(ActorSystem system) {
        MetricRegistry registry = CassandraMetricsRegistry.get(system).getRegistry();
        
        String statsdhost = system.settings().config().getString("codahale.statsd.hostname");
        int statsdport = system.settings().config().getInt("codahale.statsd.port");
        long interval = system.settings().config().getDuration("codahale.statsd.tick-interval", TimeUnit.MILLISECONDS);
        String prefix = system.settings().config().getString("codahale.statsd.prefix");
        StatsDReporter.forRegistry(registry)
            .prefixedWith(prefix)
            .build(statsdhost, statsdport)           
            .start(interval, TimeUnit.MILLISECONDS);
    }
}

package com.tradeshift.reaktive.replication;

import org.forgerock.cuppa.junit.CuppaRunner;
import org.junit.runner.RunWith;

import com.typesafe.config.ConfigFactory;

import io.vavr.collection.HashMap;

/**
 * Base class for simple actor-level unit tests that can share a basic, non-clustered actor system, with a configured in-memory journal.
 */
@RunWith(CuppaRunner.class)
public abstract class SharedActorSystemSpec extends com.tradeshift.reaktive.testkit.SharedActorSystemSpec {
    public SharedActorSystemSpec() {
        super(ConfigFactory.parseMap(HashMap
            .of("ts-reaktive.replication.local-datacenter.name","local")
            .put("ts-reaktive.replication.event-classifiers.\"com.tradeshift.reaktive.replication.TestData$TestEvent\"", TestEventClassifier.class.getName())
            .toJavaMap()
        ));
    }
}

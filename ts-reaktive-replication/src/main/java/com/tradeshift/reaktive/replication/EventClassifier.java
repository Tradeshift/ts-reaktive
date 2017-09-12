package com.tradeshift.reaktive.replication;

import io.vavr.collection.Seq;

/**
 * Determines to which additional data centers a persistenceId should be replicated after a certain event is emitted.
 */
public interface EventClassifier<E> {
    /**
     * Returns the names of any additional data centers that should get access to the
     * persistence ID of the given event, after the event has been applied, or Seq.empty()
     * if the data centers should remain unchanged.
     * 
     * This must be implemented as a pure function, relying only on the event itself. In other words,
     * the event must fully imply to which datacenters replication is to be done. This is to guarantee
     * full idempotency when replaying.
     * 
     * You can typically achieve this by having an explicit "MadeVisibleToNewDataCenter" event, or explicit
     * "alsoNowVisibleToDataCenter" fields on other events.
     */
    public Seq<String> getDataCenterNames(E event);
}

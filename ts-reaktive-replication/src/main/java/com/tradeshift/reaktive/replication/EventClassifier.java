package com.tradeshift.reaktive.replication;

import javaslang.collection.Seq;

/**
 * Determines to which additional data centers a persistenceId should be replicated after a certain event is emitted.
 */
public interface EventClassifier<E> {
    /**
     * Returns the names of any additional data centers that should get access to the 
     * persistence ID of the given event, after the event has been applied, or Seq.empty()
     * if the data centers should remain unchanged.
     */
    public Seq<String> getDataCenterNames(E event);
}

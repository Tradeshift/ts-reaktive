package com.tradeshift.reaktive.replication;

import akka.persistence.query.EventEnvelope;
import akka.stream.javadsl.Flow;

/**
 * A remote data center to which events can be sent
 */
public interface DataCenter {
    /**
     * Returns the name of this data center
     */
    String getName();
    
    /**
     * Returns a flow that can be used to upload events to this data center. Its output will periodically
     * emit the offset of events that have been successfully uploaded.
     */
    Flow<EventEnvelope,Long,?> uploadFlow();
}

package com.tradeshift.reaktive.replication;

import io.vavr.collection.HashSet;
import io.vavr.collection.Set;

/**
 * The visibility of a particular persistenceId to one or more data centers. If it is visible to a data center,
 * it should be replicated there.
 */
public class Visibility {
    /**
     * A Visibility instance describing a persistenceId for which the visibility is not yet known.
     */
    public static final Visibility EMPTY = new Visibility(HashSet.empty(), false);
    
    private final Set<String> datacenters;
    private final boolean master;
    
    /**
     * Creates a new Visibility.
     * @param datacenters The data centers to which this persistenceId should be visible
     * @param master Whether the current data center is the master. Only the master will replicate events to other data centers.
     */
    public Visibility(Set<String> datacenters, boolean master) {
        this.datacenters = datacenters;
        this.master = master;
    }

    public Set<String> getDatacenters() {
        return datacenters;
    }
    
    public boolean isMaster() {
        return master;
    }

    public boolean isVisibleTo(DataCenter dataCenter) {
        return datacenters.contains(dataCenter.getName());
    }

    /**
     * Returns a new Visibility object with the given data center name added.
     */
    public Visibility add(String dataCenter) {
        return new Visibility(datacenters.add(dataCenter), master);
    }
}

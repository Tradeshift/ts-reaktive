package com.tradeshift.reaktive.replication;

import javaslang.Tuple;
import javaslang.collection.HashMap;
import javaslang.collection.Map;
import javaslang.collection.Vector;

public abstract class DataCenterRepository {
    private final Map<String, DataCenter> remotes = HashMap.ofEntries(Vector.ofAll(listRemotes()).map(c -> Tuple.of(c.getName(), c)));

    /**
     * Returns the name of the local datacenter
     */
    public abstract String getLocalName();

    /**
     * Returns the remote datacenters that this server knows about. 
     */
    public Map<String,DataCenter> getRemotes() {
        return remotes;
    }

    /**
     * Should list the remote datacenters that this server knows about. 
     */
    protected abstract Iterable<DataCenter> listRemotes();
}

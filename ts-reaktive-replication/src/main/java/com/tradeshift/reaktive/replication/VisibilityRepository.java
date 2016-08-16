package com.tradeshift.reaktive.replication;

import java.util.Collections;
import java.util.concurrent.CompletionStage;

import com.datastax.driver.core.PreparedStatement;

import akka.Done;
import javaslang.collection.HashSet;

/**
 * Stores which persistenceIds should be visible in other data centers (in addition to the current one)
 * 
 * TODO add some caching to this class, but only considering that the thing may be clustered later. 
 */
public class VisibilityRepository {
    private final VisibilityCassandraSession session;
    private CompletionStage<PreparedStatement> getEventOffsetStmt;
    private CompletionStage<PreparedStatement> setEventOffsetStmt;
    private CompletionStage<PreparedStatement> getVisibilityStmt;
    private CompletionStage<PreparedStatement> addVisibilityStmt;
    private CompletionStage<PreparedStatement> setMasterStmt;
    
    public VisibilityRepository(VisibilityCassandraSession session) {
        this.session = session;
        String ks = session.getKeyspace();
        
        getEventOffsetStmt = session.prepare("SELECT lastEventOffset FROM " + ks + ".meta WHERE datacenter = ? AND tag = ?");
        setEventOffsetStmt = session.prepare("INSERT INTO " + ks + ".meta (datacenter, tag, lastEventOffset) VALUES (?, ?, ?)");
        getVisibilityStmt = session.prepare("SELECT master, datacenters FROM " + ks + ".visibility WHERE persistenceid = ?");
        addVisibilityStmt = session.prepare("UPDATE " + ks + ".visibility SET datacenters = datacenters + ? WHERE persistenceid = ?");
        setMasterStmt = session.prepare("UPDATE " + ks + ".visibility SET master = ? WHERE persistenceid = ?");
    }

    public CompletionStage<Long> getLastEventOffset(DataCenter dataCenter, String tag) {
        return getEventOffsetStmt
            .thenCompose(stmt -> session.selectOne(stmt.bind(dataCenter.getName(), tag), row -> row.getLong("lastEventOffset")))
            .thenApply(opt -> opt.getOrElse(0l));
    }
    
    public CompletionStage<Done> setLastEventOffset(DataCenter dataCenter, String tag, long offset) {
        return setEventOffsetStmt
            .thenCompose(stmt -> session.executeWrite(stmt.bind(dataCenter.getName(), tag, offset)));
    }

    public CompletionStage<Boolean> isVisibleTo(DataCenter target, String persistenceId) {
        return getVisibility(persistenceId).thenApply(v -> v.getDatacenters().contains(target.getName()));
    }

    /**
     * Returns the data center names to which the given persistenceId is currently visible
     */
    public CompletionStage<Visibility> getVisibility(String persistenceId) {
        return getVisibilityStmt
            .thenCompose(stmt -> session.selectOne(stmt.bind(persistenceId), row ->
                new Visibility(HashSet.ofAll(row.getSet("datacenters", String.class)), row.getBool("master"))))
            .thenApply(opt -> opt.getOrElse(Visibility.EMPTY));
    }

    public CompletionStage<Done> makeVisibleTo(DataCenter target, String persistenceId) {
        return addVisibilityStmt
            .thenCompose(stmt -> session.executeWrite(stmt.bind(Collections.singleton(target.getName()), persistenceId)));
    }
    
    public CompletionStage<Done> setMaster(String persistenceId, boolean master) {
        return setMasterStmt
            .thenCompose(stmt -> session.executeWrite(stmt.bind(master, persistenceId)));        
    }
}

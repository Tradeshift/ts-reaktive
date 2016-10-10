package com.tradeshift.reaktive.replication.actors;

import java.util.function.Function;

import com.tradeshift.reaktive.actors.PersistentActorSharding;
import com.tradeshift.reaktive.protobuf.Query;
import com.tradeshift.reaktive.replication.io.WebSocketDataCenterServer;

import akka.actor.Props;

/**
 * Extends {@link PersistentActorSharding} such that incoming replicated events can be directly routed to target actors.
 */
public class ReplicatedActorSharding<C> extends PersistentActorSharding<C> {

    /**
     * Creates a ReplicatedActorSharding for an actor that is created according to [props].
     * Entities will be sharded onto 256 shards.
     * 
     * @param persistenceIdPrefix Fixed prefix for each persistence id. This is typically the name of your aggregate root, e.g. "document" or "user".
     * @param persistenceIdPostfix Function that returns the last part of the persistence id that a command is routed to. This typically is the real ID of your entity, or UUID.
     */
    public static <C> ReplicatedActorSharding<C> of(Props props, String persistenceIdPrefix, Function<C, String> persistenceIdPostfix) {
        return new ReplicatedActorSharding<>(props, persistenceIdPrefix, persistenceIdPostfix, 256);
    }
    
    /**
     * Creates a ReplicatedActorSharding for an actor that is created according to [props].
     * 
     * @param numberOfShards Number of shards to divide all entity/persistence ids into. This can not be changed after the first run.
     * @param persistenceIdPrefix Fixed prefix for each persistence id. This is typically the name of your aggregate root, e.g. "document" or "user".
     * @param persistenceIdPostfix Function that returns the last part of the persistence id that a command is routed to. This typically is the real ID of your entity, or UUID.
     */
    public static <C> ReplicatedActorSharding<C> of(Props props, String persistenceIdPrefix, Function<C, String> persistenceIdPostfix, int numberOfShards) {
        return new ReplicatedActorSharding<>(props, persistenceIdPrefix, persistenceIdPostfix, numberOfShards);
    }
    
    protected ReplicatedActorSharding(Props props, String persistenceIdPrefix, Function<C, String> persistenceIdPostfix, int numberOfShards) {
        super(props, persistenceIdPrefix, persistenceIdPostfix, numberOfShards);
    }

    /**
     * Extends the superclass handling so that incoming {@link com.tradeshift.reaktive.protobuf.Query.EventEnvelope} messages
     * (from {@link WebSocketDataCenterServer}) are correctly routed to their target actors.
     */
    @Override
    protected String getEntityId(Object command) {
        if (command instanceof Query.EventEnvelope) {
            return ((Query.EventEnvelope)command).getPersistenceId();
        } else {
            return super.getEntityId(command);
        }
    }

}

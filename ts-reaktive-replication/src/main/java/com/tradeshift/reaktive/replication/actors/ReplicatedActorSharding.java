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
     * Creates a ReplicatedActorSharding for an actor that is created according to [props]. The actor must be a subclass of {@link ReplicatedActor}.
     * Entities will be sharded onto 256 shards.
     * 
     * @param typeName    Actor name of the top-level shard region actor. This must be unique for each PersistentActorSharding type.
     * @param props       Props that is used to instantiate child actors on shards
     * @param getEntityId Function that returns the entityId (=persistenceId, and child actor name) that a command is routed to. 
     *                    This can be a UUID or the aggregate ID of your entity, possibly prefixed with a fixed static string to differentiate it
     *                    from other types in a shared journal.
     */
    public static <C> ReplicatedActorSharding<C> of(String typeName, Props props, Function<C, String> getEntityId) {
        return new ReplicatedActorSharding<>(typeName, props, getEntityId, 256);
    }
    
    /**
     * Creates a ReplicatedActorSharding for an actor that is created according to [props]. The actor must be a subclass of {@link ReplicatedActorS}.
     *
     * @param typeName    Actor name of the top-level shard region actor. This must be unique for each PersistentActorSharding type.
     * @param props       Props that is used to instantiate child actors on shards
     * @param getEntityId Function that returns the entityId (=persistenceId, and child actor name) that a command is routed to. 
     *                    This can be a UUID or the aggregate ID of your entity, possibly prefixed with a fixed static string to differentiate it
     *                    from other types in a shared journal.
     * @param numberOfShards Number of shards to divide all entity/persistence ids into. This can not be changed after the first run.
     */
    public static <C> ReplicatedActorSharding<C> of(String typeName, Props props, Function<C, String> getEntityId, int numberOfShards) {
        return new ReplicatedActorSharding<>(typeName, props, getEntityId, numberOfShards);
    }
    
    /**
     * @deprecated Use the variant where the lambda returns the whole whole entityId, since then the implementation is in full control.  
     */
    @Deprecated
    public static <C> PersistentActorSharding<C> of(Props props, String persistenceIdPrefix, Function<C, String> persistenceIdPostfix) {
        return of(persistenceIdPrefix, props, c -> persistenceIdPrefix + "_" + persistenceIdPostfix.apply(c));
    }
    
    protected ReplicatedActorSharding(String typeName, Props props, Function<C, String> getEntityId, int numberOfShards) {
        super(typeName, props, getEntityId, numberOfShards);
    }

    /**
     * Extends the superclass handling so that incoming {@link com.tradeshift.reaktive.protobuf.Query.EventEnvelope} messages
     * (from {@link WebSocketDataCenterServer}) are correctly routed to their target actors.
     */
    @Override
    public String getEntityId(Object command) {
        if (command instanceof Query.EventEnvelope) {
            return ((Query.EventEnvelope)command).getPersistenceId();
        } else {
            return super.getEntityId(command);
        }
    }

}

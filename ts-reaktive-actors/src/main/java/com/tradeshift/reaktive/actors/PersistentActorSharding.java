package com.tradeshift.reaktive.actors;

import java.util.function.Function;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.cluster.sharding.ClusterSharding;
import akka.cluster.sharding.ClusterShardingSettings;
import akka.cluster.sharding.ShardRegion.MessageExtractor;
import akka.persistence.AbstractPersistentActor;

/**
 * Base class for setting up sharding of persistent actors that:
 * 
 * - Have their persistenceId conform to [prefix] + "_" + [id], e.g. "doc_249e1098-1cd9-4ffe-a494-73a430983590"
 * - Respond to a specific base class of commands
 * - Send the commands unchanged from the SharedRegion router onto the persistent actors themselves
 * 
 * @param C Base class of commands that the actor will respond to
 */
public class PersistentActorSharding<C> {
    private final Props props;
    private final Function<C, String> getEntityId;
    private final int numberOfShards;
    private String typeName;
    
    /**
     * Creates a PersistentActorSharding for an actor that is created according to [props]. The actor must be a subclass of {@link AbstractPersistentActor}.
     * Entities will be sharded onto 256 shards.
     * 
     * @param typeName    Actor name of the top-level shard region actor. This must be unique for each PersistentActorSharding type.
     * @param props       Props that is used to instantiate child actors on shards
     * @param getEntityId Function that returns the entityId (=persistenceId, and child actor name) that a command is routed to. 
     *                    This can be a UUID or the aggregate ID of your entity, possibly prefixed with a fixed static string to differentiate it
     *                    from other types in a shared journal.
     */
    public static <C> PersistentActorSharding<C> of(String typeName, Props props, Function<C, String> getEntityId) {
        return new PersistentActorSharding<>(typeName, props, getEntityId, 256);
    }
    
    /**
     * Creates a PersistentActorSharding for an actor that is created according to [props]. The actor must be a subclass of {@link AbstractPersistentActor}.
     *
     * @param typeName    Actor name of the top-level shard region actor. This must be unique for each PersistentActorSharding type.
     * @param props       Props that is used to instantiate child actors on shards
     * @param getEntityId Function that returns the entityId (=persistenceId, and child actor name) that a command is routed to. 
     *                    This can be a UUID or the aggregate ID of your entity, possibly prefixed with a fixed static string to differentiate it
     *                    from other types in a shared journal.
     * @param numberOfShards Number of shards to divide all entity/persistence ids into. This can not be changed after the first run.
     */
    public static <C> PersistentActorSharding<C> of(String typeName, Props props, Function<C, String> getEntityId, int numberOfShards) {
        return new PersistentActorSharding<>(typeName, props, getEntityId, numberOfShards);
    }
    
    /**
     * @deprecated Use the variant where the lambda returns the whole whole entityId, since then the implementation is in full control.  
     */
    @Deprecated
    public static <C> PersistentActorSharding<C> of(Props props, String persistenceIdPrefix, Function<C, String> persistenceIdPostfix) {
        return of(persistenceIdPrefix, props, c -> persistenceIdPrefix + "_" + persistenceIdPostfix.apply(c));
    }
    
    protected PersistentActorSharding(String typeName, Props props, Function<C, String> getEntityId, int numberOfShards) {
        this.typeName = typeName;
        this.props = props;
        this.getEntityId = getEntityId;
        this.numberOfShards = numberOfShards;
    }

    private final MessageExtractor messageExtractor = new MessageExtractor() {
        @Override
        public String entityId(Object command) {
            return getEntityId(command);
        }

        @Override
        public String shardId(Object command) {
            return getShardId(getEntityId(command));
        }
        
        @Override
        public Object entityMessage(Object command) {
            // we don't need need to unwrap messages sent to the router, they can just be
            // forwarded to the target persistent actor directly.
            return command;
        }
    };
    
    /**
     * Starts the cluster router (ShardRegion) for this persistent actor type on the given actor system,
     * and returns its ActorRef. If it's already running, just returns the ActorRef.
     */
    public ActorRef shardRegion(ActorSystem system) {
        return ClusterSharding.get(system).start(
            typeName,
            props,
            ClusterShardingSettings.create(system),
            messageExtractor);
    }
    
    /**
     * Returns the entityId (=persistenceId, and actor name) to which the given command should be routed.
     * The argument must be an instance of {@code C}.
     */
    @SuppressWarnings("unchecked")
    public String getEntityId(Object command) {
        return getEntityId.apply((C) command);
    }
    
    /**
     * Returns the shard on which the given entityId should be placed
     */
    public String getShardId(String entityId) {
        return String.valueOf(entityId.hashCode() % numberOfShards);
    }
}

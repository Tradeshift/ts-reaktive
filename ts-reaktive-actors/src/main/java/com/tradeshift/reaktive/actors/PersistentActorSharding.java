package com.tradeshift.reaktive.actors;

import java.util.UUID;
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
 * - Have their persistenceId conform to [prefix] + "_" + [uuid], e.g. "doc_249e1098-1cd9-4ffe-a494-73a430983590"
 * - Respond to a specific base class of commands
 * - Send the commands unchanged from the SharedRegion router onto the persistent actors themselves
 */
public class PersistentActorSharding<A extends AbstractPersistentActor, C> {
    private final Class<A> actorType;
    private final String entityIdPrefix;
    private final Function<C, UUID> entityIdPostfix;
    
    public static <A extends AbstractPersistentActor, C> PersistentActorSharding<A,C> of(Class<A> actorType, String entityIdPrefix, Function<C, UUID> entityIdPostfix) {
        return new PersistentActorSharding<>(actorType, entityIdPrefix, entityIdPostfix);
    }
    
    protected PersistentActorSharding(Class<A> actorType, String entityIdPrefix, Function<C, UUID> entityIdPostfix) {
        this.actorType = actorType;
        this.entityIdPrefix = entityIdPrefix;
        this.entityIdPostfix = entityIdPostfix;
    }

    private final MessageExtractor messageExtractor = new MessageExtractor() {
        private final int numberOfShards = 256;
        
        @Override
        public String entityId(Object command) {
            return getEntityId(command);
        }

        @Override
        public String shardId(Object command) {
            return String.valueOf(entityId(command).hashCode() % numberOfShards);
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
            entityIdPrefix, 
            Props.create(actorType), 
            ClusterShardingSettings.create(system), 
            messageExtractor);
    }
    
    /** 
     * Returns the UUID from a generated persistence ID.
     * @param persistenceId A persistenceId of an actor that was spawned by sending a command to it through the 
     * ActorRef returned by {@link #shardRegion}. 
     */
    public UUID getUUIDFromPersistenceId(String persistenceId) {
        return UUID.fromString(persistenceId.substring(entityIdPrefix.length() + 1));
    };
    
    @SuppressWarnings("unchecked")
    protected String getEntityId(Object command) {
        return entityIdPrefix + "_" + entityIdPostfix.apply((C) command);
    }
    
}

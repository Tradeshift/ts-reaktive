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
    private final String persistenceIdPrefix;
    private final Function<C, String> persistenceIdPostfix;
    
    /**
     * Creates a PersistentActorSharding for an actor that has a no-args constructor
     * 
     * @param actorType Persistence actor to instantiate
     * @param persistenceIdPrefix Fixed prefix for each persistence id. This is typically the name of your aggregate root, e.g. "document" or "user".
     * @param persistenceIdPostfix Function that returns the last part of the persistence id that a command is routed to. This typically is the real ID of your entity, or UUID.
     */
    public static <A extends AbstractPersistentActor, C> PersistentActorSharding<C> of(Class<A> actorType, String persistenceIdPrefix, Function<C, String> persistenceIdPostfix) {
        return new PersistentActorSharding<>(Props.create(actorType), persistenceIdPrefix, persistenceIdPostfix);
    }
    
    /**
     * Creates a PersistentActorSharding for an actor that is created according to [props]. The actor must be a subclass of {@link AbstractPersistentActor}.
     * 
     * @param persistenceIdPrefix Fixed prefix for each persistence id. This is typically the name of your aggregate root, e.g. "document" or "user".
     * @param persistenceIdPostfix Function that returns the last part of the persistence id that a command is routed to. This typically is the real ID of your entity, or UUID.
     */
    public static <C> PersistentActorSharding<C> of(Props props, String persistenceIdPrefix, Function<C, String> persistenceIdPostfix) {
        return new PersistentActorSharding<>(props, persistenceIdPrefix, persistenceIdPostfix);
    }
    
    protected PersistentActorSharding(Props props, String persistenceIdPrefix, Function<C, String> entityIdForCommand) {
        this.props = props;
        this.persistenceIdPrefix = persistenceIdPrefix;
        this.persistenceIdPostfix = entityIdForCommand;
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
            persistenceIdPrefix,
            props,
            ClusterShardingSettings.create(system),
            messageExtractor);
    }
    
    /**
     * Returns the postfix part of a generated persistence ID.
     * @param persistenceId A persistenceId of an actor that was spawned by sending a command to it through the
     * ActorRef returned by {@link #shardRegion}.
     */
    public String getPersistenceIdPostfix(String persistenceId) {
        return persistenceId.substring(persistenceIdPrefix.length() + 1);
    };
    
    /**
     * Returns the entityId (=persistenceId) to which the given command should be routed
     */
    @SuppressWarnings("unchecked")
    protected String getEntityId(Object command) {
        return persistenceIdPrefix + "_" + persistenceIdPostfix.apply((C) command);
    }
    
}

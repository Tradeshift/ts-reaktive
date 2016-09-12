package com.tradeshift.reaktive.replication.actors;

import java.util.function.Function;

import com.tradeshift.reaktive.actors.PersistentActorSharding;
import com.tradeshift.reaktive.protobuf.Query;
import com.tradeshift.reaktive.replication.io.WebSocketDataCenterServer;

import akka.actor.Props;
import akka.persistence.AbstractPersistentActor;

/**
 * Extends {@link PersistentActorSharding} such that incoming replicated events can be directly routed to target actors.
 */
public class ReplicatedActorSharding<C> extends PersistentActorSharding<C> {

    /**
     * Creates a ReplicatedActorSharding for an actor that has a no-args constructor
     */
    public static <A extends AbstractPersistentActor, C> ReplicatedActorSharding<C> of(Class<A> actorType, String persistenceIdPrefix, Function<C, String> persistenceIdPostfix) {
        return new ReplicatedActorSharding<>(Props.create(actorType), persistenceIdPrefix, persistenceIdPostfix);
    }
    
    /**
     * Creates a ReplicatedActorSharding for an actor that is created according to [props].
     */
    public static <C> ReplicatedActorSharding<C> of(Props props, String persistenceIdPrefix, Function<C, String> persistenceIdPostfix) {
        return new ReplicatedActorSharding<>(props, persistenceIdPrefix, persistenceIdPostfix);
    }
    
    protected ReplicatedActorSharding(Props props, String persistenceIdPrefix, Function<C, String> persistenceIdPostfix) {
        super(props, persistenceIdPrefix, persistenceIdPostfix);
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

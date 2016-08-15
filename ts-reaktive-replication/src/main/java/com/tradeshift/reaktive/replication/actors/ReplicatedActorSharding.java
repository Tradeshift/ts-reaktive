package com.tradeshift.reaktive.replication.actors;

import java.util.UUID;
import java.util.function.Function;

import com.tradeshift.reaktive.actors.PersistentActorSharding;
import com.tradeshift.reaktive.protobuf.Query;

import akka.actor.Props;
import akka.persistence.AbstractPersistentActor;

public class ReplicatedActorSharding<C> extends PersistentActorSharding<C> {

    /**
     * Creates a ReplicatedActorSharding for an actor that has a no-args constructor
     */
    public static <A extends AbstractPersistentActor, C> ReplicatedActorSharding<C> of(Class<A> actorType, String entityIdPrefix, Function<C, UUID> entityIdPostfix) {
        return new ReplicatedActorSharding<>(Props.create(actorType), entityIdPrefix, entityIdPostfix);
    }
    
    /**
     * Creates a ReplicatedActorSharding for an actor that is created according to [props].
     */
    public static <C> ReplicatedActorSharding<C> of(Props props, String entityIdPrefix, Function<C, UUID> entityIdPostfix) {
        return new ReplicatedActorSharding<>(props, entityIdPrefix, entityIdPostfix);
    }
    
    protected ReplicatedActorSharding(Props props, String entityIdPrefix, Function<C, UUID> entityIdPostfix) {
        super(props, entityIdPrefix, entityIdPostfix);
    }

    @Override
    protected String getEntityId(Object command) {
        if (command instanceof Query.EventEnvelope) {
            return ((Query.EventEnvelope)command).getPersistenceId();
        } else {
            return super.getEntityId(command);
        }
    }

}

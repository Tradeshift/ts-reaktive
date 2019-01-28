package com.tradeshift.reaktive.replication.actors;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicReference;

import com.tradeshift.reaktive.actors.CommandHandler;
import com.tradeshift.reaktive.actors.AbstractState;
import com.tradeshift.reaktive.actors.AbstractStatefulPersistentActor;
import com.tradeshift.reaktive.protobuf.Query;
import com.tradeshift.reaktive.replication.EventClassifier;
import com.tradeshift.reaktive.replication.Replication;
import com.tradeshift.reaktive.replication.ReplicationId;

import akka.actor.Status.Failure;
import akka.japi.pf.ReceiveBuilder;
import akka.persistence.RecoveryCompleted;
import akka.serialization.SerializationExtension;
import io.vavr.control.Option;
import static io.vavr.control.Option.none;
import static io.vavr.control.Option.some;

/**
 * An extension to {@link AbstractStatefulPersistentActor} which allows persistent actors to work across data centers and legal regions.
 * 
 * Such an actor will only accept writes in one region, and forward emitted events to other regions where they should be present.
 * 
 * Implementations must define a public, no-arguments constructor that passes in the runtime Class types of C and E.
 * 
 * The first event emitted by the first command sent to this actor MUST store the local datacenter name, and
 * EventClassifier must return that datacenter name (as first element, if multiple) when given the event.
 * 
 * The persistenceId for a replicated actor MUST NOT be allowed to be client-picked, since the system must guarantee that
 * IDs generated on one data center aren't also created on others. This can be done by using GUIDs, or simply prefixing the 
 * data center name to the persistence ID.
 * 
 * If you have existing persistent actors with already-emitted events that you are "upgrading" to become replicated actors,
 * implement the {@link #createMigrationEvent()} method to have them "pin" themselves to the current datacenter. In addition,
 * you may want to run a persistence query to start/stop all existing persistenceIDs so that they all emit their
 * migration events.
 */
public abstract class ReplicatedActor<C,E,S extends AbstractState<E,S>> extends AbstractStatefulPersistentActor<C,E,S> {
    private final Replication replication = ReplicationId.INSTANCE.get(context().system());
    private final Receive receiveRecover;

    public ReplicatedActor(Class<C> commandType, Class<E> eventType, CommandHandler<C, E, S> handler) {
        super(commandType, eventType, handler);

        Receive invokeSuper = super.createReceiveRecover();
        AtomicReference<Option<Boolean>> slave = new AtomicReference<>(none());

        this.receiveRecover = ReceiveBuilder.create()
            .match(eventType, e -> slave.get().isEmpty() && !classifier().getDataCenterNames(e).isEmpty(), e -> {
                slave.set(some(!includesLocalDataCenter(e)));
                invokeSuper.onMessage().apply(e);
            })
            .match(RecoveryCompleted.class, msg -> {
                if (slave.get().isEmpty()) {
                    if (lastSequenceNr() > 0) {
                        getContext().become(migrateNonReplicatedActor());
                    } else {
                        getContext().become(justCreated());
                    }
                } else {
                    getContext().become(slave.get().get() ? slave() : master());
                }
                if (invokeSuper.onMessage().isDefinedAt(msg)) {
                    invokeSuper.onMessage().apply(msg);
                }
            })
            .build()
            .orElse(invokeSuper);
    }
    
    protected EventClassifier<E> classifier() {
        return replication.getEventClassifier(eventType);
    }

    @Override
    public Receive createReceiveRecover() {
        return receiveRecover;
    }
    
    /**
     * We've detected an existing persistence actor (there are events for this persistenceId), however none of the events
     * have emitted any data center names. Hence, we conclude that we're migrating an old non-replicated actor,
     * and use {@link #createMigrationEvent()} to emit an extra event that DOES pin this actor the the current data center.
     */
    protected Receive migrateNonReplicatedActor() {
        log.debug("Migrating non-replicated actor {}", self().path());
        self().tell(Migrate.INSTANCE, self()); // we can't use persist() inside receiveRecover(), at least not with the cassandra plugin.

        return ReceiveBuilder.create()
            .match(Migrate.class, msg -> {
                E event = createMigrationEvent();
                if (!includesLocalDataCenter(event)) {
                    throw new IllegalStateException(
                            "createMigrationEvent() returned an event that does NOT include the local data center "
                            + replication.getLocalDataCenterName() + ", but instead " + classifier().getDataCenterNames(event));
                }
                persistEvent(event, e -> {
                    getContext().become(master());
                    unstashAll();
                });
            })
            .matchAny(other -> stash())
            .build();
    }
    
    /**
     * Implement this method to return an event E that pins the persistent actor to the current data center. 
     * The event classifier for E _MUST_ return the local data center as the first datacenter for the returned event.
     * 
     * This method will always be called just after recovery has completed, so feel free to use your existing 
     * state information to create the event, if necessary.
     * 
     * By default, this method will throw UnsupportedOperationException.
     */
    protected E createMigrationEvent() {
        throw new UnsupportedOperationException("Trying to migrate an existing non-replicated actor, but createMigrationEvent() wasn't implemented.");
    }
    
    /** 
     * The journal doesn't have any events yet for this persistenceId, which means the actor could either become a slave or a master,
     * depending on the first command.
     */
    protected Receive justCreated() {
        Receive receive = createReceive();

        return ReceiveBuilder.create()
            .match(commandType, c -> isReadOnly(c), c -> {
                log.debug("not accepting {}", c);
                sender().tell(new Failure(new UnknownActorException("Actor " + persistenceId() + " does not know yet whether it's slave or not. Try again later. Was handling:" + c)), self());
            })
            .match(commandType, c -> {
                log.debug("Received write command as first, becoming master: {}", c);
                getContext().become(master());
                if (receive.onMessage().isDefinedAt(c)) {
                    receive.onMessage().apply(c);
                }
            })
            .match(Query.EventEnvelope.class, e -> {
            	log.debug("Received event envelope as first, becoming slave.");
                getContext().become(slave());
                receiveEnvelope(e);
            })
            .build()
            .orElse(receive); // allow any non-command custom messages to just pass through to the actual actor implementation.
    }
    
    protected Receive master() {
        return ReceiveBuilder.create()
            .match(Query.EventEnvelope.class, e -> {
                log.error("Actor is not in slave mode, but was sent an EventEnvelope: {} from {} \n"
                    + "Possibly the same persistenceId was created on several datacenters independently. That will not end well.\n"
                    + "The incoming event has been ignored. The proper cause of action is to delete either this or the other aggregate.", e, sender());
                sender().tell(new Failure(new IllegalStateException("Actor is not in slave mode, but was sent an EventEnvelope. "
                    + "Possibly the same persistenceId was created on several datacenters independently. That will not end well.")), self());
            })
            .build()
            .orElse(createReceive());    	
    }
    
    protected Receive slave() {
        return ReceiveBuilder.create()
            .match(Query.EventEnvelope.class, e -> {
                receiveEnvelope(e);
            })
            .match(commandType, c -> !isReadOnly(c), c ->
                sender().tell(new Failure(new IllegalStateException("Actor is in slave mode and does not accept non-readOnly command " + c)), self())
            )
            .build()
            .orElse(createReceive());
    }
    
    protected void receiveEnvelope(Query.EventEnvelope envelope) {
        if (envelope.getSequenceNr() > lastSequenceNr() + 1) {
            // TODO: park this in a cassandra table and piece it together later.
            log.warning("Received sequence nr {}, but only at {} myself. Stashing and waiting for the rest.", envelope.getSequenceNr(), lastSequenceNr());
            stash();
        } else if (envelope.getSequenceNr() <= lastSequenceNr()) {
            log.warning("Received duplicate event {} while already at {}. Assuming idempotent.", envelope.getSequenceNr(), lastSequenceNr());
            // TODO actually check that the event is idempotent with what we already have in the journal
            sender().tell(envelope.getTimestamp(), self());
        } else if (!envelope.getPersistenceId().equals(persistenceId())) {
            throw new IllegalStateException("Received event envelope for a different actor: " + envelope.getPersistenceId());
        } else {
            log.debug("Saving event nr {}, I'm at {}", envelope.getSequenceNr(), lastSequenceNr());
            E event = SerializationExtension.get(context().system()).deserialize(envelope.getEvent().toByteArray(), eventType).get();
            persistEvent(event, e -> {
                sender().tell(envelope.getTimestamp(), self());
                unstashAll();
            });
        }
    }
    
    @Override
    protected void validateFirstEvent(E e) {
        if (!includesLocalDataCenter(e)) {
            throw new IllegalStateException("First-emitted event of a ReplicatedActor must yield local data center name (" +
                replication.getLocalDataCenterName() + ") as first element when given to EventClassifier, but instead got: " +
                classifier().getDataCenterNames(e).mkString(", "));
        }
    }
    
    /** Returns whether the given event, according to the classifier, exposes this actor's data to the local data center */
    private boolean includesLocalDataCenter(E e) {
        return classifier().getDataCenterNames(e).headOption().contains(replication.getLocalDataCenterName());
    }
    
    /**
     * Returns whether the given command is read-only, i.e. will never emit events.
     * 
     * Only read-only commands are valid to send to a GlobalActor in {@link #slave} mode.
     * 
     * The first command sent to an actor that is to be a master, must NOT be read-only.
     */
    protected abstract boolean isReadOnly(C command);
    
    private static final class Migrate implements Serializable {
        private static final long serialVersionUID = 1L;
        private static final Migrate INSTANCE = new Migrate();
    }
}

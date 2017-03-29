package com.tradeshift.reaktive.replication.actors;

import com.tradeshift.reaktive.actors.AbstractState;
import com.tradeshift.reaktive.actors.AbstractStatefulPersistentActor;
import com.tradeshift.reaktive.protobuf.Query;
import com.tradeshift.reaktive.replication.EventClassifier;
import com.tradeshift.reaktive.replication.Replication;
import com.tradeshift.reaktive.replication.ReplicationId;

import akka.actor.Status.Failure;
import akka.japi.pf.ReceiveBuilder;
import akka.serialization.SerializationExtension;
import javaslang.collection.Seq;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;

/**
 * An extension to {@link AbstractStatefulPersistentActor} which allows persistent actors to work across data centers and legal regions.
 * 
 * Such an actor will only accept writes in one region, and forward emitted events to other regions where they should be present.
 * 
 * Implementations must define a public, no-arguments constructor that passes in the runtime Class types of C and E.
 * 
 * The first event emitted by the first command sent to this actor MUST store the local datacenter name, and
 * EventClassifier must return that datacenter name (as first element, if multiple) when given the event.
 */
public abstract class ReplicatedActor<C,E,S extends AbstractState<E,S>> extends AbstractStatefulPersistentActor<C,E,S> {
    private final Replication replication = ReplicationId.INSTANCE.get(context().system());
    
    /**
     * Whether this actor is merely a slave, receiving (and persisting) incoming EventEnvelope events being sent
     * from another data center. Only one data center is the master.
     * 
     * This field will be null before the first command is received.
     * 
     * Future extensions may implement protocols to allow a different data center to become master (by not having any master
     * for a short amount of time, and eventually having the designated new master to discover that by eventing).
     */
    private Boolean slave;

    public ReplicatedActor(Class<C> commandType, Class<E> eventType) {
        super(commandType, eventType);
    }
    
    protected EventClassifier<E> classifier() {
        return replication.getEventClassifier(eventType);
    }

    @Override
    public PartialFunction<Object, BoxedUnit> receiveRecover() {
        PartialFunction<Object, BoxedUnit> invokeSuper = super.receiveRecover();
        
        return ReceiveBuilder
            .match(eventType, e -> slave == null, e -> {
                Seq<String> dataCenterNames = classifier().getDataCenterNames(e);
                if (!dataCenterNames.isEmpty()) {
                    slave = dataCenterNames.head().equals(replication.getLocalDataCenterName());
                }
                invokeSuper.apply(e);
            })
            .build()
            .orElse(invokeSuper);
    }
    
    @Override
    public PartialFunction<Object, BoxedUnit> receiveCommand() {
        return ReceiveBuilder
            .match(commandType, c -> slave != null && slave && !isReadOnly(c), c ->
                sender().tell(new Failure(new IllegalStateException("Actor is in slave mode and does not accept " + c)), self())
            )
            .match(commandType, c -> slave == null && isReadOnly(c), c -> {
                log.debug("not accepting {}", c);
                sender().tell(new Failure(new UnknownActorException("Actor " + persistenceId() + " does not know yet whether it's slave or not. Try again later. Was handling:" + c)), self());
            })
            .match(commandType, c -> isReadOnly(c), c -> {
                log.debug("Received read-only command {}", c);
                super.receiveCommand().apply(c);
            })
            .match(commandType, c -> slave == null, c -> {
                log.debug("Received write command as first {}", c);
                slave = false;
                super.receiveCommand().apply(c);
            })
            .match(commandType, c -> {
                log.debug("Received write command {}", c);
                super.receiveCommand().apply(c);
            })
            .match(Query.EventEnvelope.class, e -> slave != null && !slave, e -> {
                log.error("Actor is not in slave mode, but was sent an EventEnvelope: {} from {} \n"
                    + "Possibly the same persistenceId was created on several datacenters independently. That will not end well.\n"
                    + "The incoming event has been ignored. The proper cause of action is to delete either this or the other aggregate.", e, sender());
                sender().tell(new Failure(new IllegalStateException("Actor is not in slave mode, but was sent an EventEnvelope. "
                    + "Possibly the same persistenceId was created on several datacenters independently. That will not end well.")), self());
            })
            .match(Query.EventEnvelope.class, e -> {
                slave = true;
                receiveEnvelope(e);
            })
            .build()
            .orElse(super.receiveCommand());
    }
    
    private void receiveEnvelope(Query.EventEnvelope envelope) {
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
        Seq<String> names = classifier().getDataCenterNames(e);
        if (names.isEmpty() || !names.head().equals(replication.getLocalDataCenterName())) {
            throw new IllegalStateException("First-emitted event of a ReplicatedActor must yield local data center name when given to EventClassifier");
        }
    }
    
    /**
     * Returns whether the given command is read-only, i.e. will never emit events.
     * 
     * Only read-only commands are valid to send to a GlobalActor in {@link #slave} mode.
     * 
     * The first command sent to an actor that is to be a master, must NOT be read-only.
     */
    protected abstract boolean isReadOnly(C command);
}

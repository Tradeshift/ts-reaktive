package com.tradeshift.reaktive.akka.persistence;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ReceiveTimeout;
import akka.cluster.sharding.ShardRegion;
import akka.japi.pf.ReceiveBuilder;
import akka.persistence.AbstractPersistentActor;
import akka.persistence.SnapshotOffer;
import scala.PartialFunction;
import scala.concurrent.duration.Duration;
import scala.runtime.BoxedUnit;

/**
 * Base class for persistent actor that manages some state, receives commands of a defined type, and emits events of a defined type.
 * The actor automatically passivates itself after a configured timeout.
 *
 * @param <C> Type of commands that this actor expects to receive. 
 * @param <E> Type of events that this actor emits. 
 * @param <S> Immutable type that contains all the state the actor maintains. 
 */
public abstract class AbstractStatefulPersistentActor<C,E,S extends AbstractState<E,S>> extends AbstractPersistentActor {
    private static final Logger log = LoggerFactory.getLogger(AbstractStatefulPersistentActor.class);
    
    private S state = initialState();
    private final Class<E> eventType;
    private final Class<C> commandType;
    
    public AbstractStatefulPersistentActor(Class<C> commandType, Class<E> eventType) {
        this.commandType = commandType;
        this.eventType = eventType;
        getContext().setReceiveTimeout(Duration.fromNanos(context().system().settings().config().getDuration("documentcore.passivate-timeout").toNanos()));
    }

    @Override
    public final PartialFunction<Object, BoxedUnit> receiveCommand() {
        return ReceiveBuilder
            .match(commandType, this::handleCommand)
            .matchEquals(ReceiveTimeout.getInstance(), msg -> context().parent().tell(new ShardRegion.Passivate("stop"), self()))
            .matchEquals("stop", msg -> context().stop(self()))
            .build();
    }

    @Override
    public final PartialFunction<Object, BoxedUnit> receiveRecover() {
        return ReceiveBuilder
            .match(eventType, evt -> { state = state.apply(evt); })
            .match(SnapshotOffer.class, snapshot -> {
            // Snapshots will be handled here once we need them.
            })
            .build();
    }
    
    @Override
    public final String persistenceId() {
        return self().path().name();
    }
    
    /** Returns the current state of the actor (before having handled any incoming command) */
    protected final S getState() {
        return state;
    }

    /**
     * Returns the partial functions that handles commands sent to this actor.
     * Use ReceiveBuilder to create partial functions.
     */
    protected abstract PartialFunction<C, ? extends AbstractCommandHandler<C,E,S>> applyCommand();
    
    /**
     * Returns the initial value the state should be, when the actor is just created.
     * 
     * Although the implementation of this method is free to investigate the actor's context() and its environment, it must
     * not apply any changes (i.e. be without side-effect). 
     */
    protected abstract S initialState();
    
    private void handleCommand(C cmd) {
        log.info("{} processing {}", self().path(), cmd);
        AbstractCommandHandler<C,E,S> handler = applyCommand().apply(cmd);
        Optional<Object> error = handler.getValidationError(lastSequenceNr());
        if (error.isPresent()) {
            log.debug("  invalid: {}", error.get());
            sender().tell(error.get(), self());
        } else if (handler.isAlreadyApplied()) {
            log.debug("  was already applied.");
            sender().tell(handler.getIdempotentReply(lastSequenceNr()), self());
        } else {
            List<E> events = handler.getEventsToEmit();
            log.debug("  emitting {}", events);
            if (events.isEmpty()) {
                sender().tell(handler.getReply(events, lastSequenceNr()), self());
            } else {
                AtomicInteger need = new AtomicInteger(events.size());
                persistAll(events, evt -> {
                    state = state.apply(evt);
                    if (need.decrementAndGet() == 0) {
                        sender().tell(handler.getReply(events, lastSequenceNr()), self());                        
                    }
                });                    
            }
        }
    }
}

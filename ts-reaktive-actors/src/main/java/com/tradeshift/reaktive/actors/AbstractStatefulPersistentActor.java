package com.tradeshift.reaktive.actors;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import akka.actor.ReceiveTimeout;
import akka.cluster.sharding.ShardRegion;
import akka.event.Logging;
import akka.event.LoggingAdapter;
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
 * Implementations must define a public, no-arguments constructor that passes in the runtime Class types of C and E.
 *  
 * @param <C> Type of commands that this actor expects to receive. 
 * @param <E> Type of events that this actor emits. 
 * @param <S> Immutable type that contains all the state the actor maintains. 
 */
public abstract class AbstractStatefulPersistentActor<C,E,S extends AbstractState<E,S>> extends AbstractPersistentActor {
    protected final LoggingAdapter log = Logging.getLogger(getContext().system(), this);
    
    private S state = initialState();
    
    protected final Class<E> eventType;
    protected final Class<C> commandType;
    
    public AbstractStatefulPersistentActor(Class<C> commandType, Class<E> eventType) {
        this.commandType = commandType;
        this.eventType = eventType;
        getContext().setReceiveTimeout(Duration.fromNanos(getPassivateTimeout().toNanos()));
    }

    protected java.time.Duration getPassivateTimeout() {
        return context().system().settings().config().getDuration("ts-reaktive.actors.passivate-timeout");
    }

    @Override
    public PartialFunction<Object, BoxedUnit> receiveCommand() {
        return ReceiveBuilder
            .match(commandType, this::handleCommand)
            .matchEquals(ReceiveTimeout.getInstance(), msg -> context().parent().tell(new ShardRegion.Passivate("stop"), self()))
            .matchEquals("stop", msg -> context().stop(self()))
            .build();
    }

    @Override
    public PartialFunction<Object, BoxedUnit> receiveRecover() {
        return ReceiveBuilder
            .match(eventType, evt -> { haveApplied(evt); })
            .match(SnapshotOffer.class, snapshot -> {
                // Snapshots support is not implemented yet.
            })
            .build();
    }
    
    @Override
    public String persistenceId() {
        return self().path().name();
    }
    
    /** Returns the current state of the actor (before having handled any incoming command) */
    protected S getState() {
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
                    haveApplied(evt);
                    if (need.decrementAndGet() == 0) {
                        sender().tell(handler.getReply(events, lastSequenceNr()), self());                        
                    }
                });                    
            }
        }
    }

    /**
     * Updates the actor's internal state to match [evt] having been applied.
     * 
     * You should generally not have to call this method yourself, unless you're extending the framework.
     */
    protected void haveApplied(E evt) {
        state = state.apply(evt);
    }
}

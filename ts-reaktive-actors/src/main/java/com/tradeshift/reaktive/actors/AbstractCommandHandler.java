package com.tradeshift.reaktive.actors;

import io.vavr.collection.Seq;
import io.vavr.control.Option;

/** Base class for handlers that handle specific commands */
public abstract class AbstractCommandHandler<C,E,S extends AbstractState<E,?>> {
    /**
     * The state as it was before applying the command
     */
    protected final S state;
    /**
     * The command being received
     */
    protected final C cmd;
    protected final long now = System.currentTimeMillis();
    
    public AbstractCommandHandler(S state, C cmd) {
        this.state = state;
        this.cmd = cmd;
    }
    
    /**
     * Returns the events to be emitted as a result of applying the command
     */
    public abstract Seq<E> getEventsToEmit();
    
    /**
     * Returns the reply to send back to sender() on successfully having applied the events returned from events()
     * @param emittedEvents The list of events that was returned from getEventsToEmit() earlier.
     * @param lastSequenceNr The current last sequence number emitted on any event
     */
    public abstract Object getReply(Seq<E> emittedEvents, long lastSequenceNr);

    /**
     * Returns the reply to send back to sender() on determining that the command has already been applied and was idempotent.
     * @param lastSequenceNr The current last sequence number emitted on any event
     */
    public Object getIdempotentReply(long lastSequenceNr) {
        throw new UnsupportedOperationException("Must implement getIdempotentReply() if overriding isAlreadyApplied()");
    }

    /**
     * Returns whether the current state indicates that the command was already applied (is idempotent)
     */
    public boolean isAlreadyApplied() {
        return false;
    }

    /**
     * Validates the given command. Default implementation accepts all commands.
     * @param lastSequenceNr The current last sequence number emitted on any earlier event
     * @return Any validation error that should be sent back to sender() in case the command is invalid,
     *         or Optional.absent() in case the command is valid.
     */
    public Option<Object> getValidationError(long lastSequenceNr) {
        return Option.none();
    }
}
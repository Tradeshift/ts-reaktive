package com.tradeshift.reaktive.actors;

import java.util.concurrent.CompletionStage;

import io.vavr.collection.Seq;
import io.vavr.collection.Vector;
import io.vavr.control.Option;

/** 
 * A command handler knows how to transform command into a result, asynchronously. 
 * Command handlers that don't need to to asynchronous work can implement {@link SynchronousCommandHandler} instead.
 * 
 * Handlers can be chained like partial functions.
 * 
 * An implementation MUST implement {@link #canHandle(Object)} and {@link #handle(AbstractState, Object)}.
 */
public interface CommandHandler<C,E,S extends AbstractState<E,?>> {
    /**
     * Returns a AbstractCommandHandler that tries all of the given handlers in sequence, applying the first one that matches.
     */
    @SafeVarargs
    public static <C,E,S extends AbstractState<E,?>> CommandHandler<C,E,S> all(CommandHandler<C,E,S>... handlers) {
        return new CommandHandler<C,E,S>() {
            @Override
            public boolean canHandle(C cmd) {
                for (CommandHandler<C,E,S> handler: handlers) {
                    if (handler.canHandle(cmd)) {
                        return true;
                    }
                }
                return false;
            }
            
            @Override
            public CompletionStage<Results<E>> handle(S state, C cmd) {
                for (CommandHandler<C,E,S> handler: handlers) {
                    if (handler.canHandle(cmd)) {
                        return handler.handle(state, cmd);
                    }
                }
                throw new IllegalArgumentException("Trying to handle a command for which canHandle() returned false.");
            }
        };
    }
    
    /**
     * Returns whether this handler can handle the given command.
     */
    public abstract boolean canHandle(C cmd);
    
    /**
     * Returns the asynchronous result of handling the given command. 
     */
    public abstract CompletionStage<Results<E>> handle(S state, C cmd);
    
    /**
     * Returns a new command handler that first tries this handler and then {@code other}.
     */
    default public CommandHandler<C,E,S> orElse(CommandHandler<? super C, ? extends E, ? super S> other) {
        CommandHandler<C, E, S> me = this;
        return new CommandHandler<C,E,S>() {
            @Override
            public boolean canHandle(C cmd) {
                return me.canHandle(cmd) || other.canHandle(cmd);
            }
            
            @Override
            public CompletionStage<Results<E>> handle(S state, C cmd) {
                if (me.canHandle(cmd)) {
                    return me.handle(state, cmd);
                } else {
                    return other.handle(state, cmd).thenApply(Results::cast);
                }
            }
        };
    }
    
    /** 
     * All the information that controls what should happen as a result of handling a command.
     */
    public interface Results<E> {
        /**
         * Results are covariant in E, i.e. a Result<U> is a Result<T> when U extends T.
         */
        @SuppressWarnings("unchecked")
        public static <T,U extends T> Results<T> cast(Results<U> results) {
            return (Results<T>) results;
        }
        
        /**
         * Returns the events to be emitted as a result of applying the command. 
         * 
         * By default, no events are emitted (which is appropriate for read-only commands).
         */
        default public Seq<E> getEventsToEmit() {
            return Vector.empty();
        }
        
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
        default public Object getIdempotentReply(long lastSequenceNr) {
            throw new UnsupportedOperationException("Must implement getIdempotentReply() if overriding isAlreadyApplied()");
        }
    
        /**
         * Returns whether the current state indicates that the command was already applied (is idempotent)
         */
        default public boolean isAlreadyApplied() {
            return false;
        }
    
        /**
         * Validates the given command. Default implementation accepts all commands.
         * @param lastSequenceNr The current last sequence number emitted on any earlier event
         * @return Any validation error that should be sent back to sender() in case the command is invalid,
         *         or Optional.absent() in case the command is valid.
         */
        default public Option<Object> getValidationError(long lastSequenceNr) {
            return Option.none();
        }
    }
}
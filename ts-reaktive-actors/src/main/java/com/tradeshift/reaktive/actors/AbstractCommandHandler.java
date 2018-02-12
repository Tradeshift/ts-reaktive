package com.tradeshift.reaktive.actors;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.vavr.collection.Seq;
import io.vavr.collection.Vector;
import io.vavr.control.Option;

/** 
 * A command handler knows how to transform command into a result, asynchronously. 
 * 
 * Handlers can be chained like partial functions.
 * 
 * Implementations can extend this class to implement command handlers. An implementation MUST
 * implement either {@link #handle(AbstractState, Object)} or {@link #handleAsync(AbstractState, Object)},
 * but not both.
 */
public abstract class AbstractCommandHandler<C,E,S extends AbstractState<E,?>> {
    /**
     * Returns a AbstractCommandHandler that tries all of the given handlers in sequence, applying the first one that matches.
     */
    @SafeVarargs
    public static <C,E,S extends AbstractState<E,?>> AbstractCommandHandler<C,E,S> all(AbstractCommandHandler<C,E,S>... handlers) {
        return new AbstractCommandHandler<C,E,S>() {
            @Override
            public boolean canHandle(C cmd) {
                for (int i = 0; i < handlers.length; i++) {
                    if (handlers[i].canHandle(cmd)) {
                        return true;
                    }
                }
                return false;
            }
            
            @Override
            public CompletionStage<Results<E>> handleAsync(S state, C cmd) {
                for (int i = 0; i < handlers.length; i++) {
                    if (handlers[i].canHandle(cmd)) {
                        return handlers[i].handleAsync(state, cmd);
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
    public CompletionStage<Results<E>> handleAsync(S state, C cmd) {
        return CompletableFuture.completedFuture(handle(state, cmd));    
    }
    
    /**
     * For non-asynchronous handlers, returns the direct result of handling the given command.
     * 
     * Implementations that don't need to do any asynchronous work in order to process a command
     * can override this function (which {@link #handleAsync(AbstractState, Object)} will then invoke).
     */
    protected Results<E> handle(S state, C cmd) {
        throw new UnsupportedOperationException("Sub-classes of AbstractCommandHandler need to implement either handleAsync or handle.");
    }

    /**
     * Returns a new command handler that first tries this handler and then {@code other}.
     */
    public AbstractCommandHandler<C,E,S> orElse(AbstractCommandHandler<? super C, ? extends E, ? super S> other) {
        AbstractCommandHandler<C, E, S> me = this;
        return new AbstractCommandHandler<C,E,S>() {
            @Override
            public boolean canHandle(C cmd) {
                return me.canHandle(cmd) || other.canHandle(cmd);
            }
            
            @Override
            public CompletionStage<Results<E>> handleAsync(S state, C cmd) {
                if (me.canHandle(cmd)) {
                    return me.handleAsync(state, cmd);
                } else {
                    return other.handleAsync(state, cmd).thenApply(Results::cast);
                }
            }
        };
    }
    
    /** 
     * All the information that controls what should happen as a result of handling a command.
     */
    public static abstract class Results<E> {
        /**
         * Results are covariant in E, i.e. a Result<U> is a Result<T> when U extends T.
         */
        @SuppressWarnings("unchecked")
        public static <T,U extends T> Results<T> cast(Results<U> results) {
            return (Results<T>) results;
        }
        
        /**
         * The timestamp at which the results were created (convenient to use as timestamp in events)
         */
        protected final long now = System.currentTimeMillis();
        
        /**
         * Returns the events to be emitted as a result of applying the command. 
         * 
         * By default, no events are emitted (which is appropriate for read-only commands).
         */
        public Seq<E> getEventsToEmit() {
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
}
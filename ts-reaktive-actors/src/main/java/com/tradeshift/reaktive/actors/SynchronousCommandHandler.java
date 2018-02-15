package com.tradeshift.reaktive.actors;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Command handlers that don't need to to asynchronous work can implement this interface.
 * 
 * An implementation MUST implement {@link #canHandle(Object)} and {@link #handleSynchronously(AbstractState, Object)}.
 */
public interface SynchronousCommandHandler<C,E,S extends AbstractState<E,?>> extends CommandHandler<C,E,S> {
    /**
     * Returns the asynchronous result of handling the given command.
     * Since this is a synchronous command handler, it just invokes {@link #handleSynchronously(AbstractState, Object)}.
     */ 
    @Override
    default public CompletionStage<Results<E>> handle(S state, C cmd) {
        return CompletableFuture.completedFuture(handleSynchronously(state, cmd));
    }

    /**
     * Returns the direct result of handling the given command.
     */
    public abstract Results<E> handleSynchronously(S state, C cmd);
}

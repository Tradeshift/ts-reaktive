package com.tradeshift.reaktive.actors;

/**
 * Base class for immutable classes that hold the state for an AbstractStatefulPersistentActor 
 */
public abstract class AbstractState<E,S extends AbstractState<E,?>> {
    /**
     * Returns the state updated for the given event. 
     */
    public abstract S apply(E event);
}
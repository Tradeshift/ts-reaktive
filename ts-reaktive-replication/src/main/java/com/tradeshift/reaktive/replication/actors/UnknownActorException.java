package com.tradeshift.reaktive.replication.actors;

/**
 * Is thrown by a ReplicatedActor if it comes up and is immediately sent a read-only command.
 * 
 * That's an invalid state, since it at that point doesn't know yet whether it's to be created in this datacenter (by sending it
 * a non-readonly command) or whether it's to be a replica (by it receiving an EventEnvelope as first message).
 * 
 * In most applications, you can transform this exception into an "object not found" business logic message.
 */
public class UnknownActorException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public UnknownActorException() {
        super();
    }

    public UnknownActorException(String message, Throwable cause) {
        super(message, cause);
    }

    public UnknownActorException(String message) {
        super(message);
    }

    public UnknownActorException(Throwable cause) {
        super(cause);
    }
}

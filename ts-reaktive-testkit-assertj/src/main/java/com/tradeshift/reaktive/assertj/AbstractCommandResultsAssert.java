package com.tradeshift.reaktive.assertj;

import com.tradeshift.reaktive.actors.CommandHandler.Results;

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.AbstractIterableAssert;
import org.assertj.core.api.AbstractObjectAssert;
import org.assertj.core.api.Assertions;
import org.assertj.core.error.BasicErrorMessageFactory;

import io.vavr.collection.Seq;
import io.vavr.control.Option;

/**
 * Provides assertions for Results emitted from a command.
 *
 * Typically a subclass is written for the actual types in your project, so more type-safe variants of idempotentReply() etc. can
 * be declared.
 */
public class AbstractCommandResultsAssert<SELF extends AbstractCommandResultsAssert<SELF,E>,E>
    extends AbstractAssert<SELF, Results<E>> {

    public AbstractCommandResultsAssert(Results<E> actual, Class<?> selfType) {
        super(actual, selfType);
    }

    /**
     * Asserts there is a validation error, and allows further assertions on it.
     */
    public AbstractObjectAssert<?,?> validationError() {
        return Assertions.assertThat(validationError(Object.class));
    }

    /**
     * Asserts there is a validation error.
     */
    public SELF isInvalid() {
        validationError();
        return myself;
    }

    /**
     * Asserts there is a validation error and returns it, casting it to the given type.
     * Useful for subclasses for concrete Results implementations that have a fixed message type.
     */
    protected <M> M validationError(Class<M> type) {
        Option<Object> err = actual.getValidationError(0);
        if (err.isEmpty()) {
            throwAssertionError(new BasicErrorMessageFactory("Expected a Result with validation errors, but instead was %s",
                actualToString()));
        }
        return type.cast(err.get());
    }

    /**
     * Asserts the results are valid and already applied, and performs further assertions on the reply message.
     *
     * It is recommended that subclasses declare a more specific overload of this method, e.g.
     * <pre>
     *    public AbstractObjectAssert<?, MyResponseType> idempotentReply() {
     *      return Assertions.assertThat(idempotentReply(MyResponseType.class));
     *    }
     * </pre>
     */
    public AbstractObjectAssert<?,?> idempotentReply() {
        return Assertions.assertThat(idempotentReply(Object.class));
    }

    /**
     * Asserts the results are valid, and are already applied.
     */
    public SELF isAlreadyApplied() {
        idempotentReply();
        return myself;
    }

    /**
     * Asserts the Results are already applied (idempotent), and returns the reply message.
     * Useful for subclasses for concrete Results implementations that have a fixed message type.
     */
    protected <M> M idempotentReply(Class<M> type) {
        if (actual.getValidationError(0).isDefined() ||
            !actual.isAlreadyApplied()) {
            throwAssertionError(new BasicErrorMessageFactory("Expected a Result to be valid and already applied, but instead was %s",
                actualToString()));
        }
        return type.cast(actual.getIdempotentReply(0));
    }

    /**
     * Asserts the results are valid and NOT already applied, and performs further assertions on the reply message.
     */
    public AbstractObjectAssert<?,?> nonIdempotentReply() {
        return Assertions.assertThat(nonIdempotentReply(Object.class));
    }

    /**
     * Asserts the Results are NOT already applied, and returns the reply message.
     * Useful for subclasses for concrete Results implementations that have a fixed message type.
     */
    protected <M> M nonIdempotentReply(Class<M> type) {
        if (actual.getValidationError(0).isDefined() ||
            actual.isAlreadyApplied()) {
            throwAssertionError(new BasicErrorMessageFactory("Expected a Result to be valid and not already applied, but instead was %s",
                actualToString()));
        }
        Seq<E> events = actual.getEventsToEmit();
        return type.cast(actual.getReply(events, 0));
    }

    /**
     * Asserts the Results are not already applied, and performs further assertions on the emitted events.
     * Useful for subclasses for concrete Results implementations that have a fixed message type.
     */
    public AbstractIterableAssert<?, ? extends Iterable<? extends E>, E> events() {
        return Assertions.assertThat(getEvents());
    }

    protected Seq<E> getEvents() {
        if (actual.getValidationError(0).isDefined() ||
            actual.isAlreadyApplied()) {
            throwAssertionError(new BasicErrorMessageFactory("Expected a Result to be valid and not already applied, but instead was %s",
                actualToString()));
        }
        return actual.getEventsToEmit();
    }

    private Object actualToString() {
        if (actual.getValidationError(0).isDefined()) {
            return "Result(invalid, reply=" + actual.getValidationError(0) + ")";
        } else if (actual.isAlreadyApplied()) {
            return "Result(idempotent, reply=" + actual.getIdempotentReply(0) + ")";
        } else {
            Seq<E> events = actual.getEventsToEmit();
            return "Result(nonIdempotent, reply=" + actual.getReply(events, 0) + ", events=" + events + ")";
        }
    }
}

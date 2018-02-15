package com.tradeshift.reaktive.actors;

import static akka.pattern.PatternsCS.pipe;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValueFactory;

import akka.actor.ReceiveTimeout;
import akka.cluster.sharding.ShardRegion;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Procedure;
import akka.japi.pf.ReceiveBuilder;
import akka.persistence.AbstractPersistentActor;
import akka.persistence.SnapshotOffer;
import akka.persistence.journal.Tagged;
import io.vavr.collection.Seq;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import scala.concurrent.duration.Duration;

/**
 * Base class for persistent actor that manages some state, receives commands of a defined type, and emits events of a defined type.
 * The actor automatically passivates itself after a configured timeout.
 * The actor automatically tags emitted events with the simple class name of E (without package), or with the tag specified
 * under ts-reaktive.actors.tags.[full-class-name].
 *
 * Implementations must define a public, no-arguments constructor that passes in the runtime Class types of C and E, e.g.
 * 
 * <code><pre>
 *   public MyActor() {
 *       super(MyCommand.class, MyEvent.class);
 *   }
 * </pre></code>
 * 
 * Implementations should not use the persistAsync* variants, as they would allow internal actor state to diverge.
 * 
 * @param <C> Type of commands that this actor expects to receive.
 * @param <E> Type of events that this actor emits.
 * @param <S> Immutable type that contains all the state the actor maintains.
 */
public abstract class AbstractStatefulPersistentActor<C,E,S extends AbstractState<E,S>> extends AbstractPersistentActor {
    protected final LoggingAdapter log = Logging.getLogger(context().system(), this);
    
    private S state = initialState();

    //IDEA: We can save memory per actor by pushing these fields down into a type class, since the fields have the same value for every type of actor...
    protected final Class<E> eventType;
    protected final Class<C> commandType;
    private final String tagName;
    private final CommandHandler<C,E,S> handlers;
    
    public static String getEventTag(Config config, Class<?> eventType) {
        ConfigObject tags = config.getConfig("ts-reaktive.actors.tags").root();
        return (String) tags.getOrDefault(eventType.getName(), ConfigValueFactory.fromAnyRef(eventType.getSimpleName())).unwrapped();
    }
    
    public AbstractStatefulPersistentActor(Class<C> commandType, Class<E> eventType, CommandHandler<C,E,S> handlers) {
        this.commandType = commandType;
        this.eventType = eventType;
        this.tagName = getEventTag(context().system().settings().config(), eventType);
        this.handlers = handlers;
        context().setReceiveTimeout(Duration.fromNanos(getPassivateTimeout().toNanos()));
    }

    protected java.time.Duration getPassivateTimeout() {
        return context().system().settings().config().getDuration("ts-reaktive.actors.passivate-timeout");
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
            .match(CommandHandler.Results.class, msg -> {
                handleResults((CommandHandler.Results<E>) msg);
            })
            .match(commandType, this::canHandleCommand, this::handleCommand)
            .matchEquals(ReceiveTimeout.getInstance(), msg -> passivate())
            .match(Stop.class, msg -> context().stop(self()))
            .build();
    }

    @Override
    public Receive createReceiveRecover() {
        return ReceiveBuilder.create()
            .match(eventType, evt -> { updateState(evt); })
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
     * Returns the initial value the state should be, when the actor is just created.
     * 
     * Although the implementation of this method is free to investigate the actor's context() and its environment, it must
     * not apply any changes (i.e. be without side-effect).
     */
    protected abstract S initialState();
    
    /**
     * Returns whether any handler can handle the given command.
     */
    protected boolean canHandleCommand(C cmd) {
        return handlers.canHandle(cmd);
    }
    
    /**
     * Handles the given command, and processes its results once they come in asynchronously. 
     * 
     * Must only be invoked if {@link #canHandleCommand(Object)} has returned true for this command.
     */
    protected void handleCommand(C cmd) {
        // FIXME We need to uphold command ordering for the same sender (just like akka does for normal messages).
        // Hence, we need to stash subsequent messages from the same sender, while one message from that sender is still
        // being piped.
        pipe(handlers.handle(state, cmd), context().dispatcher()).to(self(), sender());
    }

    /**
     * Applies the results that came in from a handler, emitting any events, and responding to sender().
     */
    protected void handleResults(CommandHandler.Results<E> results) {
        Option<Object> error = results.getValidationError(lastSequenceNr());
        if (error.isDefined()) {
            log.debug("  invalid: {}", error.get());
            sender().tell(error.get(), self());
        } else if (results.isAlreadyApplied()) {
            log.debug("  was already applied.");
            sender().tell(results.getIdempotentReply(lastSequenceNr()), self());
        } else {
            Seq<E> events = results.getEventsToEmit();
            log.debug("  emitting {}", events);
            if (events.isEmpty()) {
                sender().tell(results.getReply(events, lastSequenceNr()), self());
            } else {
                if (lastSequenceNr() == 0) {
                    validateFirstEvent(events.head());
                }
                AtomicInteger need = new AtomicInteger(events.size());
                persistAllEvents(events, evt -> {
                    if (need.decrementAndGet() == 0) {
                        sender().tell(results.getReply(events, lastSequenceNr()), self());
                    }
                });
            }
        }
    }

    /**
     * Makes sure the first to-be-emitted event for this persistenceId is formatted as expected.
     * The default implementation does nothing.
     * 
     * You should generally not have to deal with this method yourself, unless you're extending the framework.
     */
    protected void validateFirstEvent(E head) { }

    /**
     * Saves an event and updates actor state after the event was successfully saved.
     */
    public void persistEvent(E event) {
        persistEvent(event, e -> {});
    }
    
    /**
     * Saves an event, updates actor state after the event was successfully saved, and then invokes the callback.
     */
    public void persistEvent(E event, Procedure<E> callback) {
        persist(event, callback);
    }
    
    /**
     * Saves an event, updates actor state after the event was successfully saved, and then invokes the callback.
     * @deprecated Use the type-safe persistEvent() variant instead.
     */
    @Override
    @Deprecated
    public <A> void persist(A event, Procedure<A> callback) {
        @SuppressWarnings("unchecked")
        E e = (E) event;
        super.persist(tagged(e), persisted -> {
            updateState(e);
            callback.apply(event);
        });
    }
    
    /**
     * Saves multiple events, updates actor state after each event is successfully saved.
     */
    public void persistAllEvents(Iterable<E> events) {
        persistAllEvents(events, e -> {});
    }
    
    /**
     * Saves multiple events, updates actor state and invoking the callback after each event is successfully saved.
     */
    public void persistAllEvents(Iterable<E> events, Procedure<E> callback) {
        persistAll(events, callback);
    }
    
    /**
     * Saves multiple events, updates actor state and invoking the callback after each event is successfully saved.
     * @deprecated Use the type-safe persistEvent() variant instead.
     */
    @SuppressWarnings("unchecked")
    @Override
    @Deprecated
    public <A> void persistAll(Iterable<A> events, Procedure<A> callback) {
        super.persistAll(tagged((Iterable<E>) events), persisted -> {
            updateState((E) persisted.payload());
            callback.apply((A) persisted.payload());
        });
    }
    
    /**
     * Wraps the given event in a Tagged object, instructing the journal to add a tag to it.
     */
    private Tagged tagged(E event) {
        Set<String> set = new HashSet<>();
        set.add(tagName);
        return new Tagged(event, set);
    }
    
    /**
     * Wraps the given event in a Tagged object, instructing the journal to add a tag to it.
     */
    private Iterable<Tagged> tagged(Iterable<E> event) {
        return Vector.ofAll(event).map(this::tagged);
    }
    
    /**
     * Updates the actor's internal state to match [evt] having been persisted.
     */
    private void updateState(E evt) {
        state = state.apply(evt);
        havePersisted(evt);
    }
    
    /**
     * Subclasses can implement this to run custom code whenever an event was found to have been persisted
     * (both directly after having been emitted and stored, or during recovery).
     * 
     *  The default implementation of this method does nothing.
     */
    protected void havePersisted(E evt) { }
    
    /**
     * Signals the parent actor (which is expected to be a ShardRegion) to passivate this actor, as a result
     * of not having received any messages for a certain amount of time.
     * 
     * You can also invoke this method directly if you want to cleanly stop the actor explicitly.
     */
    protected void passivate() {
        context().parent().tell(new ShardRegion.Passivate(STOP), self());
    }
    
    private static final class Stop implements Serializable {
        private static final long serialVersionUID = 1L;
    }
    private static final Stop STOP = new Stop();
}

package com.tradeshift.reaktive.actors;

import static akka.pattern.PatternsCS.pipe;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValueFactory;

import akka.actor.ReceiveTimeout;
import akka.cluster.sharding.ShardRegion;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Procedure;
import akka.japi.pf.PFBuilder;
import akka.japi.pf.ReceiveBuilder;
import akka.persistence.AbstractPersistentActor;
import akka.persistence.SnapshotOffer;
import akka.persistence.journal.Tagged;
import io.vavr.collection.Seq;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import scala.PartialFunction;
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

    //IDEA: We can save 8*3 bytes per actor by pushing these 3 fields down into a type class, since the fields have the same value for every type of actor...
    protected final Class<E> eventType;
    protected final Class<C> commandType;
    private final String tagName;
    
    public static String getEventTag(Config config, Class<?> eventType) {
        ConfigObject tags = config.getConfig("ts-reaktive.actors.tags").root();
        return (String) tags.getOrDefault(eventType.getName(), ConfigValueFactory.fromAnyRef(eventType.getSimpleName())).unwrapped();
    }
    
    public AbstractStatefulPersistentActor(Class<C> commandType, Class<E> eventType) {
        this.commandType = commandType;
        this.eventType = eventType;
        this.tagName = getEventTag(context().system().settings().config(), eventType);
        context().setReceiveTimeout(Duration.fromNanos(getPassivateTimeout().toNanos()));
    }

    protected java.time.Duration getPassivateTimeout() {
        return context().system().settings().config().getDuration("ts-reaktive.actors.passivate-timeout");
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
            .match(CommandWithHandler.class, m -> {
                @SuppressWarnings("unchecked") CommandWithHandler msg = m;
                handleCommand(msg.command, msg.handler);
            })
            .match(commandType, this::handleCommand)
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
     * Returns the partial function that asynchronously handles commands sent to this actor, allowing multiple
     * concurrent commands being asynchronously in-flight at the same time. Once the CompletionStage are resolved,
     * the individual handlers are of course executed sequentially using the actor's normal message processing mechanism.
     * 
     * Incoming commands are first tried using applyCommandAsync(), and then applyCommand() if unhandled.
     * 
     * The default implementation is empty, i.e. all incoming commands are handled by applyCommand().
     * 
     * Use {@link PFBuilder} to create partial functions.
     */
    protected PartialFunction<C, CompletionStage<? extends AbstractCommandHandler<C,E,S>>> applyCommandAsync() {
        return new PFBuilder<C, CompletionStage<? extends AbstractCommandHandler<C,E,S>>>().build();
    }
    
    /**
     * Returns a {@link PFBuilder} of the right type for applyCommandAsync. Use this method to trim down
     * on repeating code in your actor class.
     */
    protected PFBuilder<C, CompletionStage<? extends AbstractCommandHandler<C,E,S>>> applyCommandAsyncBuilder() {
        return new PFBuilder<>();
    }
    
    /**
     * Returns the partial function that handles commands sent to this actor.
     * Incoming commands are first tried using applyCommandAsync(), and then applyCommand() if unhandled.
     * 
     * Use {@link PFBuilder} to create partial functions.
     */
    protected abstract PartialFunction<C, ? extends AbstractCommandHandler<C,E,S>> applyCommand();
    
    /**
     * Returns the initial value the state should be, when the actor is just created.
     * 
     * Although the implementation of this method is free to investigate the actor's context() and its environment, it must
     * not apply any changes (i.e. be without side-effect).
     */
    protected abstract S initialState();
    
    /**
     * Looks up a handler using handleCommandAsync() or handleCommand(), and then invokes
     * handleCommand(cmd, handler) once the handler is resolved.
     */
    protected void handleCommand(C cmd) {
        if (applyCommandAsync().isDefinedAt(cmd)) {
            log.info("{} async processing {}", self().path(), cmd);
            pipe(applyCommandAsync().apply(cmd).thenApply(handler -> new CommandWithHandler(cmd, handler)), context().dispatcher()).to(self(), sender());
        } else {
            log.info("{} processing {}", self().path(), cmd);
            handleCommand(cmd, applyCommand().apply(cmd));
        }
    }

    /**
     * Executes the given command using the given handler, emitting any events, and responding to sender().
     */
    protected void handleCommand(C cmd, AbstractCommandHandler<C, E, S> handler) {
        Option<Object> error = handler.getValidationError(lastSequenceNr());
        if (error.isDefined()) {
            log.debug("  invalid: {}", error.get());
            sender().tell(error.get(), self());
        } else if (handler.isAlreadyApplied()) {
            log.debug("  was already applied.");
            sender().tell(handler.getIdempotentReply(lastSequenceNr()), self());
        } else {
            Seq<E> events = handler.getEventsToEmit();
            log.debug("  emitting {}", events);
            if (events.isEmpty()) {
                sender().tell(handler.getReply(events, lastSequenceNr()), self());
            } else {
                if (lastSequenceNr() == 0) {
                    validateFirstEvent(events.head());
                }
                AtomicInteger need = new AtomicInteger(events.size());
                persistAllEvents(events, evt -> {
                    if (need.decrementAndGet() == 0) {
                        sender().tell(handler.getReply(events, lastSequenceNr()), self());
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
    
    private class CommandWithHandler {
        private final C command;
        private final AbstractCommandHandler<C, E, S> handler;
        
        public CommandWithHandler(C command, AbstractCommandHandler<C, E, S> handler) {
            this.command = command;
            this.handler = handler;
        }
    }
}

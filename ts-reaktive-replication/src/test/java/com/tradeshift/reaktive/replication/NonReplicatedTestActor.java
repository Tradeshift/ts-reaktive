package com.tradeshift.reaktive.replication;

import static com.tradeshift.reaktive.protobuf.UUIDs.toJava;

import java.util.function.BiFunction;
import java.util.function.Predicate;

import com.tradeshift.reaktive.actors.AbstractCommandHandler;
import com.tradeshift.reaktive.actors.AbstractCommandHandler.Results;
import com.tradeshift.reaktive.actors.AbstractStatefulPersistentActor;
import com.tradeshift.reaktive.actors.PersistentActorSharding;
import com.tradeshift.reaktive.replication.TestData.TestCommand;
import com.tradeshift.reaktive.replication.TestData.TestEvent;

import akka.Done;
import akka.actor.Props;
import akka.actor.Status.Failure;
import io.vavr.collection.Seq;
import io.vavr.collection.Vector;
import io.vavr.control.Option;

/**
 * A non-replicated variant of ReplicatedTestActor, in order to test migrating non-replicated actors with existing
 * data, that now have become replicated actors.
 */
public class NonReplicatedTestActor extends AbstractStatefulPersistentActor<TestCommand, TestEvent, TestActorState> {
    public static final PersistentActorSharding<TestCommand> sharding =
		PersistentActorSharding.of("testactor", Props.create(NonReplicatedTestActor.class), c -> toJava(c.getAggregateId()).toString());
    
    private static class Handler extends AbstractCommandHandler<TestCommand, TestEvent, TestActorState> {
        private final Predicate<TestCommand> canHandle;
        private final BiFunction<TestActorState, TestCommand, Results<TestEvent>> handle;
        
        public Handler(Predicate<TestCommand> canHandle, BiFunction<TestActorState, TestCommand, Results<TestEvent>> handle) {
            this.canHandle = canHandle;
            this.handle = handle;
        }

        @Override
        public boolean canHandle(TestCommand cmd) {
            return canHandle.test(cmd);
        }
        
        @Override
        protected Results<TestEvent> handle(TestActorState state, TestCommand cmd) {
            return handle.apply(state, cmd);
        }        
    }

    public NonReplicatedTestActor() {
        super(TestCommand.class, TestEvent.class,
            new Handler(c -> c.hasRead(), (state, c) -> new Results<TestEvent>() {
                @Override
                public Option<Object> getValidationError(long lastSequenceNr) {
                    if (state.getMsg() == null) {
                        return Option.some(new Failure(new RuntimeException("Nothing written yet")));
                    } else {
                        return Option.none();
                    }
                }

                @Override
                public Object getReply(Seq<TestEvent> emittedEvents, long lastSequenceNr) {
                    return state.getMsg();
                }                
            }).orElse(new Handler(c -> c.hasWrite(), (state, c) -> new Results<TestEvent>() {
                @Override
                public Seq<TestEvent> getEventsToEmit() {
                    return Vector.of(TestEvent.newBuilder().setMsg(c.getWrite().getMsg()).build());
                }

                @Override
                public Object getReply(Seq<TestEvent> emittedEvents, long lastSequenceNr) {
                    return Done.getInstance();
                }
            }))
        );
    }
    
    @Override
    protected TestActorState initialState() {
        return TestActorState.EMPTY;
    }
}

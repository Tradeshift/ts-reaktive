package com.tradeshift.reaktive.replication;

import static com.tradeshift.reaktive.protobuf.UUIDs.toJava;

import java.util.function.BiFunction;
import java.util.function.Predicate;

import com.tradeshift.reaktive.actors.CommandHandler.Results;
import com.tradeshift.reaktive.actors.SynchronousCommandHandler;
import com.tradeshift.reaktive.replication.TestData.TestCommand;
import com.tradeshift.reaktive.replication.TestData.TestEvent;
import com.tradeshift.reaktive.replication.actors.ReplicatedActor;
import com.tradeshift.reaktive.replication.actors.ReplicatedActorSharding;

import akka.Done;
import akka.actor.Props;
import akka.actor.Status.Failure;
import io.vavr.collection.Seq;
import io.vavr.collection.Vector;
import io.vavr.control.Option;

public class ReplicatedTestActor extends ReplicatedActor<TestCommand, TestEvent, TestActorState> {
    public static final ReplicatedActorSharding<TestCommand> sharding =
        ReplicatedActorSharding.of("testactor", Props.create(ReplicatedTestActor.class), c -> toJava(c.getAggregateId()).toString());
    
    private static class Handler implements SynchronousCommandHandler<TestCommand, TestEvent, TestActorState> {
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
        public Results<TestEvent> handleSynchronously(TestActorState state, TestCommand cmd) {
            return handle.apply(state, cmd);
        }
    }

    public ReplicatedTestActor() {
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
    protected boolean isReadOnly(TestCommand command) {
        return command.hasRead();
    }

    @Override
    protected TestActorState initialState() {
        return TestActorState.EMPTY;
    }
    
    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(String.class, s -> {
                sender().tell("You sent: " + s, self());
            })
            .build()
            .orElse(super.createReceive());
    }

    @Override
    protected TestEvent createMigrationEvent() {
    	return TestEvent.newBuilder().setMsg("dc:local").build();
    }    
}

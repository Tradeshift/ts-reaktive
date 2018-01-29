package com.tradeshift.reaktive.replication;

import static com.tradeshift.reaktive.protobuf.UUIDs.toJava;

import com.tradeshift.reaktive.actors.AbstractCommandHandler;
import com.tradeshift.reaktive.replication.TestData.TestCommand;
import com.tradeshift.reaktive.replication.TestData.TestEvent;
import com.tradeshift.reaktive.replication.actors.ReplicatedActor;
import com.tradeshift.reaktive.replication.actors.ReplicatedActorSharding;

import akka.Done;
import akka.actor.Props;
import akka.actor.Status.Failure;
import akka.japi.pf.PFBuilder;
import io.vavr.collection.Seq;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import scala.PartialFunction;

public class ReplicatedTestActor extends ReplicatedActor<TestCommand, TestEvent, TestActorState> {
    public static final ReplicatedActorSharding<TestCommand> sharding =
        ReplicatedActorSharding.of("testactor", Props.create(ReplicatedTestActor.class), c -> toJava(c.getAggregateId()).toString());

    public ReplicatedTestActor() {
        super(TestCommand.class, TestEvent.class);
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
    protected PartialFunction<TestCommand, ? extends AbstractCommandHandler<TestCommand, TestEvent, TestActorState>> applyCommand() {
        return new PFBuilder<TestCommand,Handler>()
            .match(TestCommand.class, c -> c.hasRead(), c -> new Handler(c) {
                @Override
                public Option<Object> getValidationError(long lastSequenceNr) {
                    if (state.getMsg() == null) {
                        return Option.some(new Failure(new RuntimeException("Nothing written yet")));
                    } else {
                        return Option.none();
                    }
                }
                
                @Override
                public Seq<TestEvent> getEventsToEmit() {
                    return Vector.empty();
                }

                @Override
                public Object getReply(Seq<TestEvent> emittedEvents, long lastSequenceNr) {
                    return state.getMsg();
                }
            })
            .match(TestCommand.class, c -> c.hasWrite(), c -> new Handler(c) {
                @Override
                public Seq<TestEvent> getEventsToEmit() {
                    return Vector.of(TestEvent.newBuilder().setMsg(c.getWrite().getMsg()).build());
                }

                @Override
                public Object getReply(Seq<TestEvent> emittedEvents, long lastSequenceNr) {
                    return Done.getInstance();
                }
            })
            .build();
            
    }
    
    @Override
    protected TestEvent createMigrationEvent() {
    	return TestEvent.newBuilder().setMsg("dc:local").build();
    }
    
    private abstract class Handler extends AbstractCommandHandler<TestCommand, TestEvent, TestActorState> {
        public Handler(TestCommand cmd) {
            super(getState(), cmd);
        }
    }
}

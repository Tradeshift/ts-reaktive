package com.tradeshift.reaktive.replication;

import static com.tradeshift.reaktive.protobuf.UUIDs.toJava;

import com.tradeshift.reaktive.actors.AbstractCommandHandler;
import com.tradeshift.reaktive.actors.AbstractStatefulPersistentActor;
import com.tradeshift.reaktive.actors.PersistentActorSharding;
import com.tradeshift.reaktive.replication.TestData.TestCommand;
import com.tradeshift.reaktive.replication.TestData.TestEvent;

import akka.Done;
import akka.actor.Props;
import akka.actor.Status.Failure;
import akka.japi.pf.PFBuilder;
import io.vavr.collection.Seq;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import scala.PartialFunction;

/**
 * A non-replicated variant of ReplicatedTestActor, in order to test migrating non-replicated actors with existing
 * data, that now have become replicated actors.
 */
public class NonReplicatedTestActor extends AbstractStatefulPersistentActor<TestCommand, TestEvent, TestActorState> {
    public static final PersistentActorSharding<TestCommand> sharding =
		PersistentActorSharding.of("testactor", Props.create(NonReplicatedTestActor.class), c -> toJava(c.getAggregateId()).toString());

    public NonReplicatedTestActor() {
        super(TestCommand.class, TestEvent.class);
    }
    
    @Override
    protected TestActorState initialState() {
        return TestActorState.EMPTY;
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
    
    private abstract class Handler extends AbstractCommandHandler<TestCommand, TestEvent, TestActorState> {
        public Handler(TestCommand cmd) {
            super(getState(), cmd);
        }
    }
}

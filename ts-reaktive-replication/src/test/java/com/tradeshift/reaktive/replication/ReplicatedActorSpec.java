package com.tradeshift.reaktive.replication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.cuppa.Cuppa.beforeEach;
import static org.forgerock.cuppa.Cuppa.describe;
import static org.forgerock.cuppa.Cuppa.it;
import static org.forgerock.cuppa.Cuppa.when;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import com.google.protobuf.ByteString;
import com.tradeshift.reaktive.protobuf.Query;
import com.tradeshift.reaktive.protobuf.Types;
import com.tradeshift.reaktive.replication.TestData.TestCommand;
import com.tradeshift.reaktive.replication.TestData.TestEvent;

import akka.Done;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.Status.Failure;
import akka.testkit.TestProbe;
import scala.concurrent.duration.Duration;

public class ReplicatedActorSpec extends SharedActorSystemSpec {
    TestCommand.Builder cmd() {
        // aggregateId won't be used, since we don't use cluster sharding in this unit test
        return TestCommand.newBuilder().setAggregateId(Types.UUID.newBuilder().setLeastSignificantBits(0).setMostSignificantBits(0));
    }
    
    ActorRef actor;
    
{
    describe("a simple ReplicatedActor", () -> {
        final TestProbe sender = TestProbe.apply(system);
        beforeEach(() -> {
            actor = system.actorOf(Props.create(TestActor.class, () -> new TestActor()), "testactor" + new Random().nextInt(Integer.MAX_VALUE));
        });
        
        when("receiving a read-only command as first message", () -> {
            beforeEach(() -> {
                actor.tell(cmd().setRead(TestCommand.Read.newBuilder()).build(), sender.ref());
            });
            
            it("should fail the message, since it doesn't know yet whether it's a master or slave", () -> {
                sender.expectMsgClass(Failure.class);
            });
        });
        
        when("receiving a write command as first message", () -> {
            beforeEach(() -> {
                actor.tell(cmd().setWrite(TestCommand.Write.newBuilder().setMsg("dc:local")).build(), sender.ref());
                sender.expectMsgClass(Done.class);
            });
            
            it("should accept the command, and accept read-only commands after that, since it's now a master", () -> {
                actor.tell(cmd().setRead(TestCommand.Read.newBuilder()).build(), sender.ref());
                sender.expectMsg("dc:local");
            });
            
            it("should not accept a subsequent EventEnvelope message, since they should only go to slaves", () -> {
                actor.tell(Query.EventEnvelope.newBuilder().build(), sender.ref());
                Failure failure = sender.expectMsgClass(Failure.class);
                assertThat(failure.cause().getMessage()).contains("same persistenceId was created on several datacenters");
            });
        });
        
        when("receiving write command that end up emitting an event which doesn't include the local data center name", () -> {
            it("should throw an exception and get killed by the supervisor, since that's not a valid start state", () -> {
                sender.watch(actor);
                actor.tell(cmd().setWrite(TestCommand.Write.newBuilder().setMsg("notadatacenter")).build(), sender.ref());
                sender.expectTerminated(actor, Duration.create(1, TimeUnit.SECONDS));
            });
        });
        
        when("receiving an EventEnvelope that originated remotely as first message", () -> {
            beforeEach(() -> {
                actor.tell(Query.EventEnvelope.newBuilder()
                    .setEvent(
                        ByteString.copyFrom(TestEvent.newBuilder().setMsg("dc:remote").build().toByteArray())
                    )
                    .setTimestamp(1000l)
                    .setPersistenceId(actor.path().name())
                    .setSequenceNr(1l)
                .build(), sender.ref());
                sender.expectMsg(1000l);
            });
            
            it("should fail write commands, since they should only go to the master", () -> {
                actor.tell(cmd().setWrite(TestCommand.Write.newBuilder().setMsg("world")).build(), sender.ref());
                sender.expectMsgClass(Failure.class);
            });
            
            it("should ignore an EventEnvelope with the same or earlier sequence number", () -> {
                actor.tell(Query.EventEnvelope.newBuilder()
                    .setTimestamp(1000l)
                    .setPersistenceId(actor.path().name())
                    .setSequenceNr(1l)
                .build(), sender.ref());
                sender.expectMsg(1000l);
            });
            
            it("should eventually accept read-only commands, since a slave can answer read-only requests", () -> {
                actor.tell(cmd().setRead(TestCommand.Read.newBuilder()).build(), sender.ref());
                sender.expectMsg("dc:remote");
            });
                
            it("should persist received EventEnvelopes so that when it restarts, it arrives at the same state", () -> {
                sender.watch(actor);
                actor.tell(PoisonPill.getInstance(), sender.ref());
                sender.expectTerminated(actor, Duration.create(1, TimeUnit.SECONDS));
                
                ActorRef restarted = system.actorOf(Props.create(TestActor.class, () -> new TestActor()), actor.path().name());
                restarted.tell(cmd().setRead(TestCommand.Read.newBuilder()).build(), sender.ref());
                sender.expectMsg("dc:remote");
            });
            
            it("should stash an EventEnvelope with a sequence number more than 1 ahead, and pick it up later", () -> {
                actor.tell(Query.EventEnvelope.newBuilder()
                    .setEvent(
                        ByteString.copyFrom(TestEvent.newBuilder().setMsg("third").build().toByteArray())
                    )
                    .setTimestamp(3000l)
                    .setPersistenceId(actor.path().name())
                    .setSequenceNr(3l)
                .build(), sender.ref());
                
                actor.tell(Query.EventEnvelope.newBuilder()
                    .setEvent(
                        ByteString.copyFrom(TestEvent.newBuilder().setMsg("second").build().toByteArray())
                    )
                    .setTimestamp(2000l)
                    .setPersistenceId(actor.path().name())
                    .setSequenceNr(2l)
                .build(), sender.ref());
                
                sender.expectMsg(2000);
                sender.expectMsg(3000);
                
                actor.tell(cmd().setRead(TestCommand.Read.newBuilder()).build(), sender.ref());
                sender.expectMsg("third");
            });
        });
    });
}}

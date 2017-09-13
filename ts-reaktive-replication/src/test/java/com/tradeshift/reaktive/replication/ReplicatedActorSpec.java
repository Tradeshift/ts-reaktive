package com.tradeshift.reaktive.replication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.cuppa.Cuppa.only;
import static org.forgerock.cuppa.Cuppa.beforeEach;
import static org.forgerock.cuppa.Cuppa.describe;
import static org.forgerock.cuppa.Cuppa.it;
import static org.forgerock.cuppa.Cuppa.when;

import java.util.Random;

import com.google.protobuf.ByteString;
import com.tradeshift.reaktive.protobuf.Query;
import com.tradeshift.reaktive.protobuf.Types;
import com.tradeshift.reaktive.replication.TestData.TestCommand;
import com.tradeshift.reaktive.replication.TestData.TestEvent;
import com.tradeshift.reaktive.replication.TestData.TestCommand.Write;

import akka.Done;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.Status.Failure;
import akka.persistence.query.EventEnvelope;
import akka.testkit.javadsl.TestKit;
import io.vavr.collection.Seq;

public class ReplicatedActorSpec extends SharedActorSystemSpec {
    TestCommand.Builder cmd() {
        // aggregateId won't be used, since we don't use cluster sharding in this unit test
        return TestCommand.newBuilder().setAggregateId(Types.UUID.newBuilder().setLeastSignificantBits(0).setMostSignificantBits(0));
    }
    
    ActorRef actor;
    
{
    describe("a simple ReplicatedActor", () -> {
        final TestKit sender = new TestKit(system);
        beforeEach(() -> {
            actor = system.actorOf(Props.create(ReplicatedTestActor.class, () -> new ReplicatedTestActor()), "testactor" + new Random().nextInt(Integer.MAX_VALUE));
        });
        
        when("receiving a read-only command as first message", () -> {
            beforeEach(() -> {
                actor.tell(cmd().setRead(TestCommand.Read.newBuilder()).build(), sender.getRef());
            });
            
            it("should fail the message, since it doesn't know yet whether it's a master or slave", () -> {
                sender.expectMsgClass(Failure.class);
            });
        });
        
        when("receiving a write command as first message", () -> {
            beforeEach(() -> {
                actor.tell(cmd().setWrite(TestCommand.Write.newBuilder().setMsg("dc:local")).build(), sender.getRef());
                sender.expectMsgClass(Done.class);
            });
            
            it("should accept the command, and accept read-only commands after that, since it's now a master", () -> {
                actor.tell(cmd().setRead(TestCommand.Read.newBuilder()).build(), sender.getRef());
                sender.expectMsg("dc:local");
            });
            
            it("should not accept a subsequent EventEnvelope message, since they should only go to slaves", () -> {
                actor.tell(Query.EventEnvelope.newBuilder().build(), sender.getRef());
                Failure failure = sender.expectMsgClass(Failure.class);
                assertThat(failure.cause().getMessage()).contains("same persistenceId was created on several datacenters");
            });
        });
        
        when("receiving write command that end up emitting an event which doesn't include the local data center name", () -> {
            it("should throw an exception and get killed by the supervisor, since that's not a valid start state", () -> {
                sender.watch(actor);
                actor.tell(cmd().setWrite(TestCommand.Write.newBuilder().setMsg("notadatacenter")).build(), sender.getRef());
                sender.expectTerminated(actor);
            });
        });
        
        when("recovering into a datacenter with the same name as its first already emitted event", () -> {
            beforeEach(() -> {
                actor.tell(cmd().setWrite(TestCommand.Write.newBuilder().setMsg("dc:local")).build(), sender.getRef());
                sender.expectMsgClass(Done.class);
                sender.watch(actor);
                system.stop(actor);
                sender.expectTerminated(actor);
                Thread.sleep(300); // allow actor system to make the name available again.
            });
            
            it("should become a master, since it's running in its original data center", () -> {
                // restart the actor
                actor = system.actorOf(Props.create(ReplicatedTestActor.class, () -> new ReplicatedTestActor()), actor.path().name());
                // should accept writes
                actor.tell(cmd().setWrite(TestCommand.Write.newBuilder().setMsg("hello")).build(), sender.getRef());
                sender.expectMsgClass(Done.class);
            });
        });
        
        when("recovering into a datacenter with a different name as its first already emitted event", () -> {
            beforeEach(() -> {
                // FakeRemoteTestActor will inject a simulated event into the journal that'll indicate it was from a remote datacenter
                ActorRef fake = system.actorOf(Props.create(FakeRemoteTestActor.class, () -> new FakeRemoteTestActor("dc:other")), "remoteTest");
                sender.watch(fake);
                sender.expectTerminated(fake);
            });
            
            it("should become a slave, since it's running in a different data center than where it originated", () -> {
                // restart the actor
                actor = system.actorOf(Props.create(ReplicatedTestActor.class, () -> new ReplicatedTestActor()), "remoteTest");
                // should not accept writes
                actor.tell(cmd().setWrite(TestCommand.Write.newBuilder().setMsg("hello")).build(), sender.getRef());
                Failure f = sender.expectMsgClass(Failure.class);
                assertThat(f.cause()).hasMessageContaining("Actor is in slave mode and does not accept");
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
                .build(), sender.getRef());
                sender.expectMsg(1000l);
            });
            
            it("should fail write commands, since they should only go to the master", () -> {
                actor.tell(cmd().setWrite(TestCommand.Write.newBuilder().setMsg("world")).build(), sender.getRef());
                sender.expectMsgClass(Failure.class);
            });
            
            it("should ignore an EventEnvelope with the same or earlier sequence number", () -> {
                actor.tell(Query.EventEnvelope.newBuilder()
                    .setTimestamp(1000l)
                    .setPersistenceId(actor.path().name())
                    .setSequenceNr(1l)
                .build(), sender.getRef());
                sender.expectMsg(1000l);
            });
            
            it("should eventually accept read-only commands, since a slave can answer read-only requests", () -> {
                actor.tell(cmd().setRead(TestCommand.Read.newBuilder()).build(), sender.getRef());
                sender.expectMsg("dc:remote");
            });
                
            it("should persist received EventEnvelopes so that when it restarts, it arrives at the same state", () -> {
                sender.watch(actor);
                actor.tell(PoisonPill.getInstance(), sender.getRef());
                sender.expectTerminated(actor);
                
                ActorRef restarted = system.actorOf(Props.create(ReplicatedTestActor.class, () -> new ReplicatedTestActor()), actor.path().name());
                restarted.tell(cmd().setRead(TestCommand.Read.newBuilder()).build(), sender.getRef());
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
                .build(), sender.getRef());
                
                actor.tell(Query.EventEnvelope.newBuilder()
                    .setEvent(
                        ByteString.copyFrom(TestEvent.newBuilder().setMsg("second").build().toByteArray())
                    )
                    .setTimestamp(2000l)
                    .setPersistenceId(actor.path().name())
                    .setSequenceNr(2l)
                .build(), sender.getRef());
                
                sender.expectMsg(2000);
                sender.expectMsg(3000);
                
                actor.tell(cmd().setRead(TestCommand.Read.newBuilder()).build(), sender.getRef());
                sender.expectMsg("third");
            });
        });
    });
    
    only().describe("A replicated actor that is migrated from non-replicated existing events", () -> {
        final TestKit sender = new TestKit(system);
        beforeEach(() -> {
            actor = system.actorOf(Props.create(NonReplicatedTestActor.class, () -> new NonReplicatedTestActor()), "testactor" + new Random().nextInt(Integer.MAX_VALUE));
            sender.send(actor, cmd().setWrite(Write.newBuilder().setMsg("hello")).build());
            sender.expectMsg(Done.getInstance());

            sender.watch(actor);
            system.stop(actor);
            sender.expectTerminated(actor);
            Thread.sleep(100); // allow name to be re-used        	
            actor = system.actorOf(Props.create(ReplicatedTestActor.class, () -> new ReplicatedTestActor()), actor.path().name());
        });
        
        it("should emit the extra datacenter event pinning it to the local datacenter on recovery, but only once", () -> {
            actor.tell(cmd().setRead(TestCommand.Read.newBuilder()).build(), sender.getRef());
            sender.expectMsg("dc:local"); // because createMigrationEvent() has emitted this.
            
            Seq<EventEnvelope> afterFirstRestart = journalEventsFor(actor.path().name());
            assertThat(TestEvent.class.cast(afterFirstRestart.last().event()).getMsg()).isEqualTo("dc:local");

            // Stop, start, and wait for it to be ready again
            sender.watch(actor);
            system.stop(actor);
            sender.expectTerminated(actor);
            Thread.sleep(100); // allow name to be re-used
            actor = system.actorOf(Props.create(ReplicatedTestActor.class, () -> new ReplicatedTestActor()), actor.path().name());
            actor.tell(cmd().setRead(TestCommand.Read.newBuilder()).build(), sender.getRef());
            sender.expectMsg("dc:local");

            Seq<EventEnvelope> afterSecondRestart = journalEventsFor(actor.path().name());
            assertThat(afterSecondRestart).isEqualTo(afterFirstRestart);
        });
    });
}}

package com.tradeshift.reaktive.backup;

import static org.forgerock.cuppa.Cuppa.beforeEach;
import static org.forgerock.cuppa.Cuppa.describe;
import static org.forgerock.cuppa.Cuppa.it;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.forgerock.cuppa.junit.CuppaRunner;
import org.junit.runner.RunWith;

import com.tradeshift.reaktive.protobuf.Query;
import com.tradeshift.reaktive.testkit.SharedActorSystemSpec;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.stream.alpakka.s3.javadsl.ListBucketResultContents;
import akka.stream.javadsl.Source;
import akka.testkit.javadsl.TestKit;
import javaslang.collection.Vector;

@RunWith(CuppaRunner.class)
public class S3RestoreSpec extends SharedActorSystemSpec {
    public S3RestoreSpec() {
        super(ConfigFactory.parseString("ts-reaktive.backup.restore.update-accuracy = 1 millisecond"));
    }
    
    private final S3 s3 = mock(S3.class);
    private final TestKit shardRegion = new TestKit(system);
    
    private ActorRef actor() {
        return system.actorOf(Props.create(S3Restore.class, () -> new S3Restore(s3, "MyEvent", shardRegion.getRef())));
    }
    
    {
        describe("The S3Restore actor", () -> {
            beforeEach(() -> {
                reset(s3);
                when(s3.list("MyEvent")).thenReturn(Source.from(Vector.of(
            		ListBucketResultContents.apply("", "prefix/MyEvent-from-2016_11_09_13_29_28_030", "", 100, Instant.now(), ""),
            		ListBucketResultContents.apply("", "prefix/MyEvent-from-2016_11_09_13_31_11_259", "", 100, Instant.now(), ""))));
                when(s3.loadEvents("MyEvent-from-2016_11_09_13_29_28_030")).thenReturn(Source.from(Vector.of(eventEnvelope(1478698168030l, 0), eventEnvelope(1478698168031l, 1))));
                when(s3.loadEvents("MyEvent-from-2016_11_09_13_31_11_259")).thenReturn(Source.from(Vector.of(eventEnvelope(1478698271259l, 2), eventEnvelope(1478698271260l, 3))));
            });
            
            it("should send event envelopes to the shard region for all events in order, and then stop. When resumed, do so at a proper offset.", () -> {
                ActorRef actor = actor();
                
                shardRegion.expectMsgEquals(eventEnvelope(1478698168030l,0));
                shardRegion.reply(1478698168030l);
                shardRegion.expectMsgEquals(eventEnvelope(1478698168031l,1));
                shardRegion.reply(1478698168031l);
                shardRegion.expectMsgEquals(eventEnvelope(1478698271259l,2));
                shardRegion.reply(1478698271259l);
                shardRegion.expectMsgEquals(eventEnvelope(1478698271260l,3));
                shardRegion.reply(1478698271260l);
                
                TestKit probe = new TestKit(system);
                probe.watch(actor);
                probe.expectTerminated(actor);
                
                actor = actor();
                // Expecting to resume at the last S3 entry, since the actor doesn't have a way of knowing whether it had been completed (only lastOffset is saved).
                shardRegion.expectMsgEquals(eventEnvelope(1478698271259l,2));
                shardRegion.reply(2l);
            });
        });
    }

    private com.tradeshift.reaktive.protobuf.Query.EventEnvelope eventEnvelope(long seqnr, long offset) {
        return Query.EventEnvelope.newBuilder()
            .setPersistenceId("pid")
            .setSequenceNr(seqnr)
            .setTimestamp(offset)
            .build();
    }
}

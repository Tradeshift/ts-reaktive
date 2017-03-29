package com.tradeshift.reaktive.backup;

import static com.tradeshift.reaktive.testkit.Await.within;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.forgerock.cuppa.Cuppa.beforeEach;
import static org.forgerock.cuppa.Cuppa.describe;
import static org.forgerock.cuppa.Cuppa.it;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.forgerock.cuppa.junit.CuppaRunner;
import org.junit.runner.RunWith;

import com.tradeshift.reaktive.akka.UUIDs;
import com.tradeshift.reaktive.testkit.SharedActorSystemSpec;
import com.typesafe.config.ConfigFactory;

import akka.Done;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.persistence.query.EventEnvelope2;
import akka.persistence.query.NoOffset;
import akka.persistence.query.TimeBasedUUID;
import akka.persistence.query.javadsl.EventsByTagQuery2;
import akka.stream.javadsl.Source;
import akka.testkit.JavaTestKit;
import javaslang.collection.Vector;

@RunWith(CuppaRunner.class)
public class S3BackupSpec extends SharedActorSystemSpec {
    private final EventsByTagQuery2 query = mock(EventsByTagQuery2.class);
    private final S3 s3 = mock(S3.class);
    
    public S3BackupSpec() {
        super(ConfigFactory.parseString(
            "ts-reaktive.backup.backup.event-chunk-max-size = 2\n"
          + "ts-reaktive.backup.backup.event-chunk-max-duration = 1 second\n"));
    }

    private ActorRef actor() {
        return system.actorOf(Props.create(S3Backup.class, () -> new S3Backup(query, "tag", s3)));
    }
    
    {
        describe("The S3Backup actor", () -> {
            beforeEach(() -> {
                reset(query, s3);
                when(s3.loadOffset()).thenReturn(completedFuture(0l));
                when(s3.saveOffset(anyLong())).thenReturn(completedFuture(Done.getInstance()));
            });
            
            it("stops itself if the query stream ends", () -> {
                when(query.eventsByTag("tag", NoOffset.getInstance())).thenReturn(Source.empty());
                
                ActorRef actor = actor();
                
                JavaTestKit probe = new JavaTestKit(system);
                probe.watch(actor);
                probe.expectTerminated(actor);
            });
            
            it("stops itself if the query stream fails", () -> {
                when(query.eventsByTag("tag", NoOffset.getInstance())).thenReturn(Source.failed(new RuntimeException("simulated failure")));
                
                ActorRef actor = actor();
                
                JavaTestKit probe = new JavaTestKit(system);
                probe.watch(actor);
                probe.expectTerminated(actor);
            });
            
            it("uploads a chunk after the specified chunk interval elapses", () -> {
                EventEnvelope2 envelope1 = EventEnvelope2.apply(new TimeBasedUUID(UUIDs.startOf(1l)), "persistenceId", 0, "hello, world");
                
                CompletableFuture<EventEnvelope2> event1 = new CompletableFuture<>();
                CompletableFuture<EventEnvelope2> event2 = new CompletableFuture<>();
                when(query.eventsByTag("tag", NoOffset.getInstance())).thenReturn(Source.fromCompletionStage(event1).concat(Source.fromCompletionStage(event2)));
                when(s3.store("tag", Vector.of(envelope1))).thenReturn(completedFuture(Done.getInstance()));
                
                actor();
                event1.complete(envelope1);
                Thread.sleep(1500);
                
                verify(s3).store("tag", Vector.of(envelope1));
            });
            
            it("uploads a chunk after the specified number of events, even if the interval hasn't elapsed yet", () -> {
                EventEnvelope2 envelope1 = EventEnvelope2.apply(new TimeBasedUUID(UUIDs.startOf(1l)), "persistenceId", 0, "hello, world");
                EventEnvelope2 envelope2 = EventEnvelope2.apply(new TimeBasedUUID(UUIDs.startOf(2l)), "persistenceId", 1, "hello, world");
                
                CompletableFuture<EventEnvelope2> event1 = new CompletableFuture<>();
                CompletableFuture<EventEnvelope2> event2 = new CompletableFuture<>();
                CompletableFuture<EventEnvelope2> event3 = new CompletableFuture<>();
                when(query.eventsByTag("tag", NoOffset.getInstance())).thenReturn(Source.fromCompletionStage(event1).concat(Source.fromCompletionStage(event2)).concat(Source.fromCompletionStage(event3)));
                when(s3.store("tag", Vector.of(envelope1, envelope2))).thenReturn(completedFuture(Done.getInstance()));
                
                actor();
                event1.complete(envelope1);
                event2.complete(envelope2);

                // before event-chunk-max-duration, the events should have been stored
                within(500, TimeUnit.MILLISECONDS).eventuallyDo(() -> {
                    verify(s3).store("tag", Vector.of(envelope1, envelope2));
                });
            });
        });
    }
}

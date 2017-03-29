package com.tradeshift.reaktive.replication;

import static com.tradeshift.reaktive.testkit.Await.eventuallyDo;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.cuppa.Cuppa.describe;
import static org.forgerock.cuppa.Cuppa.it;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import com.tradeshift.reaktive.akka.UUIDs;
import com.tradeshift.reaktive.replication.TestData.TestEvent;

import akka.Done;
import akka.actor.Props;
import akka.persistence.query.EventEnvelope;
import akka.persistence.query.EventEnvelope2;
import akka.persistence.query.NoOffset;
import akka.persistence.query.TimeBasedUUID;
import akka.persistence.query.javadsl.CurrentEventsByPersistenceIdQuery;
import akka.persistence.query.javadsl.EventsByTagQuery2;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Source;
import javaslang.collection.HashMap;
import javaslang.collection.HashSet;
import javaslang.collection.Map;

public class DataCenterForwarderSpec extends SharedActorSystemSpec {
    private static final CompletableFuture<Done> DONE = completedFuture(Done.getInstance());
    
    private class TestDataCenter implements DataCenter {
        private final String name;
        private final ConcurrentLinkedQueue<EventEnvelope> events = new ConcurrentLinkedQueue<>();
        
        public TestDataCenter(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Flow<EventEnvelope2, Long, ?> uploadFlow() {
            return Flow.<EventEnvelope2>create()
                .map(event -> {
                    events.add(envelope2to1(event));
                    return UUIDs.unixTimestamp(TimeBasedUUID.class.cast(event.offset()).value());
                });
        }
    }
    
    private static EventEnvelope envelope2to1(EventEnvelope2 e) {
        return new EventEnvelope(UUIDs.unixTimestamp(TimeBasedUUID.class.cast(e.offset()).value()), e.persistenceId(), e.sequenceNr(), e.event());
    }
    
    private TestEvent event(String msg) {
        return TestEvent.newBuilder().setMsg(msg).build();
    }
{
    describe("DataCenterForwarder", () -> {
        it("should forward events to data centers as indicated by EventRepository.getDataCenterNames", () -> {
            TestDataCenter remote1 = new TestDataCenter("remote1");
            TestDataCenter remote2 = new TestDataCenter("remote2");
            
            DataCenterRepository dataRepo = mock(DataCenterRepository.class);
            when(dataRepo.getLocalName()).thenReturn("local");
            when(dataRepo.getRemotes()).thenReturn(Map.narrow(HashMap.of(remote1.getName(), remote1).put(remote2.getName(), remote2)));
            
            VisibilityRepository visibilityRepo = mock(VisibilityRepository.class);
            AtomicReference<Visibility> visibility = new AtomicReference<>(Visibility.EMPTY);
            doAnswer(i -> completedFuture(visibility.get())).when(visibilityRepo).getVisibility("doc1");
            doAnswer(i -> completedFuture(visibility.get().getDatacenters().contains(remote1.name))).when(visibilityRepo).isVisibleTo(remote1, "doc1");
            doAnswer(i -> completedFuture(visibility.get().getDatacenters().contains(remote2.name))).when(visibilityRepo).isVisibleTo(remote2, "doc1");
            doAnswer(inv -> {
                visibility.updateAndGet(v -> v.add(remote1.name));
                return completedFuture(Done.getInstance());
            }).when(visibilityRepo).makeVisibleTo(remote1, "doc1");
            doAnswer(inv -> {
                visibility.updateAndGet(v -> v.add(remote2.name));
                return completedFuture(Done.getInstance());
            }).when(visibilityRepo).makeVisibleTo(remote2, "doc1");
            doAnswer(inv -> {
                visibility.updateAndGet(v -> new Visibility(v.getDatacenters(), true));
                return completedFuture(Done.getInstance());
            }).when(visibilityRepo).setMaster("doc1", true);
            
            AtomicLong lastOffset1 = new AtomicLong();
            AtomicLong lastOffset2 = new AtomicLong();
            doAnswer(i -> completedFuture(lastOffset1.get())).when(visibilityRepo).getLastEventOffset(remote1, "TestEvent");
            doAnswer(i -> completedFuture(lastOffset2.get())).when(visibilityRepo).getLastEventOffset(remote2, "TestEvent");
            doAnswer(i -> {lastOffset1.set(i.getArgumentAt(2, Long.class)); return DONE; }).when(visibilityRepo).setLastEventOffset(eq(remote1), eq("TestEvent"), anyLong());
            doAnswer(i -> {lastOffset2.set(i.getArgumentAt(2, Long.class)); return DONE; }).when(visibilityRepo).setLastEventOffset(eq(remote2), eq("TestEvent"), anyLong());

            EventEnvelope event1 = EventEnvelope.apply(1, "doc1", 1, event("dc:local"));
            EventEnvelope event2 = EventEnvelope.apply(2, "doc1", 2, event("dc:" + remote1.name));
            EventEnvelope event3 = EventEnvelope.apply(3, "doc1", 3, event("dc:" + remote2.name));
            EventEnvelope event4 = EventEnvelope.apply(4, "doc1", 4, event("hello"));
            CompletableFuture<EventEnvelope> realTimeEvent = new CompletableFuture<>(); // this will be completed with event4 later on in the test
            
            EventsByTagQuery2 qTag = mock(EventsByTagQuery2.class);
            // FIXME this test is racey. Sometimes event4 doesn't get seen. If it persists, rewrite this source to be queue or actor
            // driven, and make sure it's only read once.
            when(qTag.eventsByTag("TestEvent", NoOffset.getInstance())).thenReturn(
                Source.from(Arrays.asList(event1, event2, event3))
                .concat(Source.fromCompletionStage(realTimeEvent))
                .map(DataCenterForwarder::envelope1to2)
                .concat(Source.maybe()) // never complete this stream
            );
            
            CurrentEventsByPersistenceIdQuery qPid = mock(CurrentEventsByPersistenceIdQuery.class);
            when(qPid.currentEventsByPersistenceId("doc1", 0, Long.MAX_VALUE)).thenReturn(Source.from(Arrays.asList(event1, event2, event3)));
            
            system.actorOf(Props.create(DataCenterForwarder.class, () -> new DataCenterForwarder<>(materializer, remote1, visibilityRepo, TestEvent.class, qTag, qPid)));
            system.actorOf(Props.create(DataCenterForwarder.class, () -> new DataCenterForwarder<>(materializer, remote2, visibilityRepo, TestEvent.class, qTag, qPid)));
            
            eventuallyDo(() -> {
                assertThat(remote1.events).contains(event1, event2, event3);
                assertThat(remote2.events).contains(event1, event2, event3);
                assertThat(visibility.get().isMaster()).isTrue();
                assertThat(visibility.get().getDatacenters()).containsOnly(remote1.name, remote2.name);
            });
            
            // Now that the events have arrived, let's send a real-time event
            realTimeEvent.complete(event4);
            
            eventuallyDo(() -> {
                // We can't assert that lastEventOffset is updated, since the implementation doesn't sequence handling
                // of forwarded events, and handling of visibility updates.
                
                assertThat(remote1.events).contains(event4);
                assertThat(remote2.events).contains(event4);
            });
        });
        
        it("should roll back the event offset a bit when resuming operations, to compensate for clock drifts and eventual consistency", () -> {
            TestDataCenter remote1 = new TestDataCenter("remote1");
            
            DataCenterRepository dataRepo = mock(DataCenterRepository.class);
            when(dataRepo.getLocalName()).thenReturn("local");
            when(dataRepo.getRemotes()).thenReturn(HashMap.of(remote1.getName(), remote1));
            
            VisibilityRepository visibilityRepo = mock(VisibilityRepository.class);
            doAnswer(i -> completedFuture(HashSet.empty())).when(visibilityRepo).getVisibility("doc1");
            doAnswer(i -> completedFuture(false)).when(visibilityRepo).isVisibleTo(remote1, "doc1");
            doAnswer(i -> completedFuture(100000l)).when(visibilityRepo).getLastEventOffset(remote1, "TestEvent");
            
            CompletableFuture<EventEnvelope2> realTimeEvent = new CompletableFuture<>(); // this will be completed with event3 later on in the test
            
            EventsByTagQuery2 qTag = mock(EventsByTagQuery2.class);
            when(qTag.eventsByTag(eq("TestEvent"), any())).thenReturn(Source.fromCompletionStage(realTimeEvent));
            CurrentEventsByPersistenceIdQuery qPid = mock(CurrentEventsByPersistenceIdQuery.class);
            
            system.actorOf(Props.create(DataCenterForwarder.class, () -> new DataCenterForwarder<>(materializer, remote1, visibilityRepo, TestEvent.class, qTag, qPid)));
            
            eventuallyDo(() -> {
                verify(qTag).eventsByTag("TestEvent", new TimeBasedUUID(UUIDs.startOf(100000l - config.getDuration("ts-reaktive.replication.allowed-clock-drift").toMillis())));
            });
        });
        
        it("should not forward events for persistenceIds that have their master datacenter somewhere else", () -> {
            TestDataCenter remote1 = new TestDataCenter("remote1");
            
            DataCenterRepository dataRepo = mock(DataCenterRepository.class);
            when(dataRepo.getLocalName()).thenReturn("local");
            when(dataRepo.getRemotes()).thenReturn(Map.narrow(HashMap.of(remote1.getName(), remote1)));
            
            VisibilityRepository visibilityRepo = mock(VisibilityRepository.class);
            AtomicReference<Visibility> visibility = new AtomicReference<>(Visibility.EMPTY);
            doAnswer(i -> completedFuture(visibility.get())).when(visibilityRepo).getVisibility("doc1");
            doAnswer(i -> completedFuture(visibility.get().getDatacenters().contains("remote1"))).when(visibilityRepo).isVisibleTo(remote1, "doc1");
            doAnswer(inv -> {
                visibility.updateAndGet(v -> v.add("remote1"));
                return completedFuture(Done.getInstance());
            }).when(visibilityRepo).makeVisibleTo(remote1, "doc1");
            doAnswer(inv -> {
                return completedFuture(Done.getInstance());
            }).when(visibilityRepo).setMaster("doc1", false);
            
            AtomicLong lastOffset1 = new AtomicLong();
            doAnswer(i -> completedFuture(lastOffset1.get())).when(visibilityRepo).getLastEventOffset(remote1, "TestEvent");
            doAnswer(i -> {lastOffset1.set(i.getArgumentAt(2, Long.class)); return DONE; }).when(visibilityRepo).setLastEventOffset(eq(remote1), eq("TestEvent"), anyLong());
            
            EventEnvelope event1 = EventEnvelope.apply(1, "doc1", 1, event("dc:remote1"));
            EventEnvelope event2 = EventEnvelope.apply(2, "doc1", 2, event("dc:local")); // replicate to "local", hence "local" is a RO slave and not master.
            
            EventsByTagQuery2 qTag = mock(EventsByTagQuery2.class);
            when(qTag.eventsByTag("TestEvent", NoOffset.getInstance())).thenReturn(Source.from(Arrays.asList(event1, event2)).map(DataCenterForwarder::envelope1to2));
            
            CurrentEventsByPersistenceIdQuery qPid = mock(CurrentEventsByPersistenceIdQuery.class);
            when(qPid.currentEventsByPersistenceId("doc1", 0, Long.MAX_VALUE)).thenReturn(Source.from(Arrays.asList(event1, event2)));
            
            system.actorOf(Props.create(DataCenterForwarder.class, () -> new DataCenterForwarder<>(materializer, remote1, visibilityRepo, TestEvent.class, qTag, qPid)));
            
            Thread.sleep(200); // allow the actor to do some work
            verify(visibilityRepo).setMaster("doc1", false);
            assertThat(remote1.events).isEmpty();
            assertThat(visibility.get().isMaster()).isFalse();
            assertThat(visibility.get().getDatacenters()).isEmpty();
        });
    });
}
}

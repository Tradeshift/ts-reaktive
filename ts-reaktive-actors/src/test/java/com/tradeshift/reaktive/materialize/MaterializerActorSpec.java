package com.tradeshift.reaktive.materialize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.cuppa.Cuppa.beforeEach;
import static org.forgerock.cuppa.Cuppa.describe;
import static org.forgerock.cuppa.Cuppa.it;
import static org.forgerock.cuppa.Cuppa.when;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import com.tradeshift.reaktive.materialize.MaterializerActor.CreateWorker;
import com.tradeshift.reaktive.testkit.SharedActorSystemSpec;
import com.typesafe.config.ConfigFactory;

import org.forgerock.cuppa.junit.CuppaRunner;
import org.junit.runner.RunWith;

import akka.Done;
import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.stream.javadsl.Source;
import akka.testkit.javadsl.TestKit;
import io.vavr.collection.HashSet;
import io.vavr.collection.Set;
import io.vavr.collection.Stream;
import io.vavr.collection.Vector;

@RunWith(CuppaRunner.class)
public class MaterializerActorSpec extends SharedActorSystemSpec {
    public MaterializerActorSpec() {
        super(ConfigFactory.parseString("ts-reaktive.actors.materializer.batch-size = 256"));
    }

    private final int N = 100; // number of events to test with

    private TestKit materialized;

    /** Asserts that we receive the given events, in any order, while permitting duplicates. */
    private void assertReceiveOutOfOrder(Iterable<Envelope> events) {
        long timeout = System.currentTimeMillis() + 5000;

        Set<Envelope> received = HashSet.empty();
        while (System.currentTimeMillis() < timeout && !received.containsAll(events)) {
            received = received.add(materialized.expectMsgClass(Envelope.class));
        }

        assertThat(received).containsOnlyElementsOf(events);
    }

    {
        describe("MaterializerActor", () -> {
            beforeEach(() -> {
                materialized = new TestKit(system);
            });

            when("importing events for the same entity", () -> {
                Vector<Envelope> events = Stream.range(0, N).map(i ->
                    new Envelope(Instant.ofEpochMilli(1000000 + i * 1000), "entity", i)
                ).toVector();

                it("runs sequentially when using a single worker", () -> {
                    ActorRef actor = system.actorOf(Props.create(TestActor.class, () ->
                        new TestActor(Source.from(events), materialized.getRef())));

                    events.forEach(materialized::expectMsgEquals);

                    system.stop(actor);
                });

                it("runs concurrently if a second worker is started", () -> {
                    ActorRef actor = system.actorOf(Props.create(TestActor.class, () ->
                        new TestActor(Source.from(events), materialized.getRef())));
                    actor.tell(new CreateWorker(Instant.ofEpochMilli(1000000 + (N/2) * 1000)), system.deadLetters());

                    assertReceiveOutOfOrder(events);

                    system.stop(actor);
                });
            });


            when("importing events for different entities", () -> {
                Vector<Envelope> events = Stream.range(0, 100).map(i ->
                    new Envelope(Instant.ofEpochMilli(1000000 + i * 1000), "entity" + i, i)
                ).toVector();

                it("runs concurrently", () -> {
                    ActorRef actor = system.actorOf(Props.create(TestActor.class, () ->
                        new TestActor(Source.from(events), materialized.getRef())));

                    assertReceiveOutOfOrder(events);

                    system.stop(actor);
                });
            });
        });
    }

    static class Envelope {
        private final Instant timestamp;
        private final String entityId;
        private final int index;

        public Envelope(Instant timestamp, String entityId, int index) {
            this.timestamp = timestamp;
            this.entityId = entityId;
            this.index = index;
        }

        @Override
        public boolean equals(Object b) {
            Envelope e = (Envelope) b;
            return e.timestamp.equals(timestamp) && e.entityId.equals(entityId) && e.index == index;
        }

        @Override
        public int hashCode() {
            return timestamp.hashCode() ^ entityId.hashCode() ^ index;
        }

        @Override
        public String toString() {
            return entityId + "  at " + timestamp + ": " + index;
        }
    }

    static class TestActor extends MaterializerActor<Envelope> {
        private final Source<Envelope,NotUsed> events;
        private final ActorRef materialized;

        public TestActor(Source<Envelope,NotUsed> events, ActorRef materialized) {
            this.events = events;
            this.materialized = materialized;
        }

        @Override
        protected CompletionStage<Done> materialize(Envelope envelope) {
            CompletableFuture<Done> f = new CompletableFuture<>();
            getContext().getSystem().scheduler().scheduleOnce(Duration.ofMillis(10), () -> {
                materialized.tell(envelope, self());
                f.complete(Done.getInstance());
            }, getContext().dispatcher());
            return f;
        }

        @Override
        protected String getEntityId(Envelope envelope) {
            return envelope.entityId;
        }

        @Override
        protected Source<Envelope, NotUsed> loadEvents(Instant since) {
            return events.dropWhile(e -> e.timestamp.isBefore(since));
        }

        @Override
        public Instant timestampOf(Envelope envelope) {
            return envelope.timestamp;
        }

    }
}

package com.tradeshift.reaktive.replication;

import static akka.pattern.PatternsCS.pipe;

import java.util.concurrent.TimeUnit;

import com.datastax.driver.core.utils.UUIDs;

import akka.Done;
import akka.NotUsed;
import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.Status.Failure;
import akka.cluster.singleton.ClusterSingletonManager;
import akka.cluster.singleton.ClusterSingletonManagerSettings;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Backoff;
import akka.pattern.BackoffSupervisor;
import akka.persistence.query.EventEnvelope;
import akka.persistence.query.NoOffset;
import akka.persistence.query.TimeBasedUUID;
import akka.persistence.query.javadsl.CurrentEventsByPersistenceIdQuery;
import akka.persistence.query.javadsl.EventsByTagQuery;
import akka.stream.Materializer;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import javaslang.Tuple;
import javaslang.collection.Seq;
import scala.concurrent.duration.Duration;

/**
 * Runs a continuous query for all events matching a certain tag, and forwards those events to a remote data center.
 */
public class DataCenterForwarder<E> extends AbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().system(), this);
    
    /**
     * Starts a DataCenterForwarder for each of the known data centers in the {@link DataCenterRepository}.
     * @param system Actor system to create the DataCenterForwarder actors in
     * @param dataRepo Repository that knows about all data centers
     * @param materializer Akka streams materializer to use
     * @param visibilityRepo Repository that stores the current visiblity of aggregates
     * @param eventRepo Classifier that determines which additional datacenters an event should trigger replication for
     * @param eventsByTagQuery Query to use to find a continuous stream of all events
     * @param tag Tag to pass to {@link EventsByTagQuery} (all events must be tagged by this)
     * @param currentEventsByPersistenceIdQuery Query to find all current events for a specific persistenceId
     */
    public static <E> void startAll(ActorSystem system, Materializer materializer, DataCenterRepository dataRepo, VisibilityRepository visibilityRepo, Class<E> eventType,
        EventsByTagQuery eventsByTagQuery, CurrentEventsByPersistenceIdQuery currentEventsByPersistenceIdQuery) {
        
        String tag = Replication.get(system).getEventTag(eventType);
        for (DataCenter dataCenter: dataRepo.getRemotes().values()) {
            system.actorOf(ClusterSingletonManager.props(
                BackoffSupervisor.props(
                    Backoff.onFailure(
                        Props.create(DataCenterForwarder.class, () -> new DataCenterForwarder<>(materializer, dataCenter, visibilityRepo, eventType,
                            eventsByTagQuery, currentEventsByPersistenceIdQuery)),
                        "f",
                        Duration.create(1, TimeUnit.SECONDS),
                        Duration.create(1, TimeUnit.SECONDS), // TODO make these 3 configurable
                        0.2)
                ),
                Done.getInstance(),
                ClusterSingletonManagerSettings.create(system).withSingletonName("s")), "forwarder_" + dataCenter.getName() + "_" + tag);
        }
        
    }
    
    private final VisibilityRepository visibilityRepo;
    private final EventClassifier<E> classifier;
    private final CurrentEventsByPersistenceIdQuery currentEventsByPersistenceIdQuery;
    private final Materializer materializer;
    private final String tag;
    private final DataCenter dataCenter;
    private final int parallelism;
    private final String localDataCenterName;
    private final EventsByTagQuery eventsByTagQuery;
    
    private long updatingVisibilityOffset = 0;
    private int updatingVisibilityOffsetCount = 0;
    private long lastDeliveredEventOffset = 0;
    
    private long lastEventOffset;
    
    /**
     * Creates a new DataCenterForwarder and starts to forward events to a data center.
     * @param materializer Akka streams materializer to use
     * @param dataCenter Target data center to forward events to.
     * @param visibilityRepo Repository that stores the current visiblity of aggregates
     * @param eventRepo Classifier that determines which additional datacenters an event should trigger replication for
     * @param eventsByTagQuery Query to use to find a continuous stream of all events
     * @param tag Tag to pass to {@link EventsByTagQuery} (all events must be tagged by this)
     * @param currentEventsByPersistenceIdQuery Query to find all current events for a specific persistenceId
     */
    public DataCenterForwarder(Materializer materializer, DataCenter dataCenter, VisibilityRepository visibilityRepo, Class<E> eventType,
        EventsByTagQuery eventsByTagQuery, CurrentEventsByPersistenceIdQuery currentEventsByPersistenceIdQuery) {
        
		final Replication replication = Replication.get(context().system());
        
		this.eventsByTagQuery = eventsByTagQuery;
        this.materializer = materializer;
        this.visibilityRepo = visibilityRepo;
        this.classifier = replication.getEventClassifier(eventType);
        this.dataCenter = dataCenter;
        this.tag = replication.getEventTag(eventType);
        this.localDataCenterName = replication.getLocalDataCenterName();
        this.currentEventsByPersistenceIdQuery = currentEventsByPersistenceIdQuery;
        this.parallelism = context().system().settings().config().getInt("ts-reaktive.replication.parallellism");

        pipe(visibilityRepo.getLastEventOffset(dataCenter, tag).thenApply(LastEventOffsetKnown::new), context().dispatcher()).to(self());
        log.debug("Started");
    }
    
    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
            .match(LastEventOffsetKnown.class, msg -> {
                log.debug("Last offset known is {}", msg.offset);
                lastEventOffset = Math.max(0, msg.offset - context().system().settings().config().getDuration("ts-reaktive.replication.allowed-clock-drift").toMillis());
                eventsByTagQuery.eventsByTag(tag, lastEventOffset == 0 ? NoOffset.getInstance() : new TimeBasedUUID(UUIDs.startOf(lastEventOffset)))
                    .alsoTo(filteredDataCenterSink())
                    .alsoTo(stopOnError("eventsByTag"))
                    .runWith(updateVisibility(), materializer);
            })
            .match(UpdatingVisibility.class, msg -> {
                log.debug("Updating visibility for {} at {}", msg.offset, updatingVisibilityOffset);
                if (msg.offset > updatingVisibilityOffset) {
                    updatingVisibilityOffset = msg.offset;
                    updatingVisibilityOffsetCount = 1;
                } else if (msg.offset == updatingVisibilityOffset) {
                    updatingVisibilityOffsetCount++;
                }
            })
            .match(VisibilityUpdated.class, msg -> {
                log.debug("Updated visibility for {} at {}", msg.offset, updatingVisibilityOffset);
                if (msg.offset == updatingVisibilityOffset) {
                    if (updatingVisibilityOffsetCount >= 1) {
                        updatingVisibilityOffsetCount--;
                    }
                    updateLastEventOffset();
                }
            })
            .match(EventDelivered.class, msg -> {
                log.debug("Delivered an event at {}", msg.offset);
                 if (msg.offset > lastDeliveredEventOffset) {
                     lastDeliveredEventOffset = msg.offset;
                     updateLastEventOffset();
                 }
            })
            .match(Done.class, msg -> {})
            .match(Failure.class, msg -> {
                log.error(msg.cause(), "A future created from this actor has failed");
                throw (RuntimeException) msg.cause();
            })
            .build();
    }
    
    private void updateLastEventOffset() {
        log.debug("Considering updating event offset, count={}, visibility={}, lastDelivered={}", updatingVisibilityOffsetCount, updatingVisibilityOffset, lastDeliveredEventOffset);
        if (updatingVisibilityOffsetCount == 0) {
         // we're no longer updating visibility.
         // Hence, visibility has been updated at least until updatingVisibilityOffset.
         // and normal events at least until lastDeliveredEventOffset.
            final long offset =
                (updatingVisibilityOffset == 0) ? lastDeliveredEventOffset :
                (lastDeliveredEventOffset == 0) ? updatingVisibilityOffset :
                Math.min(updatingVisibilityOffset, lastDeliveredEventOffset);
            
            if (offset > lastEventOffset) {
                lastEventOffset = offset;
                log.info("lastEventOffset now {}", lastEventOffset);
                pipe(visibilityRepo.setLastEventOffset(dataCenter, tag, lastEventOffset), context().dispatcher()).to(self());
                if (updatingVisibilityOffset == offset) {
                    updatingVisibilityOffset = 0;
                }
                if (lastDeliveredEventOffset == offset) {
                    lastDeliveredEventOffset = 0;
                }
            }
        }
    }
    
    private Sink<EventEnvelope,NotUsed> filteredDataCenterSink() {
        log.debug("filteredDataCenterSink()");
        return Flow.<EventEnvelope>create()
            .mapAsync(parallelism, e -> {
                return visibilityRepo.isVisibleTo(dataCenter, e.persistenceId()).thenApply(v -> {
                    log.debug("Visibility of {}: {}", e, v);
                    return Tuple.of(e,v);});
            })
            .filter(t -> t._2)
            .map(t -> t._1)
            .via(dataCenter.uploadFlow())
            .map(EventDelivered::new)
            .to(Sink.actorRef(self(), new Failure(new IllegalStateException("Remote datacenter closed connection"))));
    }

    
    
    @SuppressWarnings("unchecked")
    private Sink<EventEnvelope,NotUsed> updateVisibility() {
        ActorRef self = self(); // not safe to close over self() inside e.g. mapAsync
        
        return Flow.<EventEnvelope>create()
            .mapAsync(parallelism, e -> {
                log.debug("updateVisibility {}", e);
                Seq<String> names = classifier.getDataCenterNames((E) e.event());
                if (e.sequenceNr() == 1) { // First event, which should contain the master data center name
                    boolean weAreMaster = !names.isEmpty() && names.head().equals(localDataCenterName);
                    boolean shouldMakeVisible = weAreMaster && names.contains(dataCenter.getName());
                    log.debug("initial master:{} / visible:{}", weAreMaster, shouldMakeVisible);
                    return visibilityRepo.setMaster(e.persistenceId(), weAreMaster).thenApply(done -> Tuple.of(e, shouldMakeVisible));
                } else {
                    return visibilityRepo.getVisibility(e.persistenceId()).thenApply(v -> {
                        log.debug("visibility of {} is {}/{}", e, v.isMaster(), v.getDatacenters());
                        return Tuple.of(e, v.isMaster() && !v.isVisibleTo(dataCenter));});
                }
            })
            .filter(t -> t._2)
            .map(t -> t._1)
            .mapAsync(parallelism, e ->
                visibilityRepo.makeVisibleTo(dataCenter, e.persistenceId()).thenApply(done -> {
                    self.tell(new UpdatingVisibility(getTimestamp(e)), self);
                    return e;
                })
            )
            .mapAsyncUnordered(parallelism, e -> {
                // TODO (optimization) don't (or queue) replay when a current replay already is in progress.
                // The above probably should be done at the same time as having clustered (non-persistent) actors per persistenceId.
                // Currently, subsequent events for the same persistenceId will trigger lots of "WARN: Received duplicate event" in ReplicatedActor.
                log.info("{} Replaying persistence ID {} into {}", self(), e.persistenceId(), dataCenter.getName());
                return currentEventsByPersistenceIdQuery.currentEventsByPersistenceId(e.persistenceId(), 0, Long.MAX_VALUE)
                                .alsoTo(stopOnError("currentEventsByPersistenceId"))
                                .via(dataCenter.uploadFlow())
                                .runWith(Sink.ignore(), materializer)
                                .thenApply(done -> e);
            })
            .alsoTo(stopOnError("updateVisibility"))
            .to(Sink.foreach(event -> self.tell(new VisibilityUpdated(getTimestamp(event)), self)));
    }
    
    private static long getTimestamp(EventEnvelope e) {
        return UUIDs.unixTimestamp(TimeBasedUUID.class.cast(e.offset()).value());
    }

    private <T> Sink<T,NotUsed> stopOnError(String msg) {
        return Sink.<T>onComplete(done -> {
            if (done.isFailure()) {
                log.error(done.failed().get(), "Failure in {}", msg);
                done.get();
                //throw (RuntimeException) done.failed().get();
            } else {
                log.debug("{} has stopped", msg);
            }
        });
    }
    
    private static class LastEventOffsetKnown {
        private final Long offset;

        private LastEventOffsetKnown(Long offset) {
            this.offset = offset;
        }
    }
    
    private static class UpdatingVisibility {
        private final long offset;

        private UpdatingVisibility(long offset) {
            this.offset = offset;
        }
    }
    
    private static class VisibilityUpdated {
        private final long offset;

        private VisibilityUpdated(long offset) {
            this.offset = offset;
        }
    }
    
    private static class EventDelivered {
        private final long offset;
        
        private EventDelivered(long offset) {
            this.offset = offset;
        }
    }
}

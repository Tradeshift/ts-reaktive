package com.tradeshift.reaktive.throttle;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.AbstractActor;
import akka.actor.ReceiveTimeout;
import akka.cluster.sharding.ShardRegion;
import scala.concurrent.duration.FiniteDuration;

/**
 * A ThrottleActor handles the throtting decision for an individual user. One ThrottleActor is created
 * for each user (each unique "key" passed to ThrottleDirective.throttle). They automatically passivate
 * themselves once a user's request quota has been filled up again. 
 */
public class ThrottleActor extends AbstractActor {
    private static final Logger log = LoggerFactory.getLogger(ThrottleActor.class);
    
    private final int tokensPerRefill;
    private final Duration refill;
    private final int maximumBurst;
    
    private int value;
    private Instant lastUpdate;

    public ThrottleActor(int tokensPerRefill, Duration refill, int maximumBurst) {
        this.tokensPerRefill = tokensPerRefill;
        this.refill = refill;
        this.maximumBurst = maximumBurst;
        this.value = maximumBurst;
        this.lastUpdate = Instant.now();
        
        // We've reached maximumBurst for sure after not seeing any requests for this long:
        Duration receiveTimeout = refill.multipliedBy((long) Math.ceil( (double)maximumBurst / tokensPerRefill));
        context().setReceiveTimeout(FiniteDuration.fromNanos(receiveTimeout.toNanos()));
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(String.class, key -> handleRequest())
            .match(ReceiveTimeout.class, msg -> passivate())
            .match(Stop.class, msg -> context().stop(self()))
            .build();
    }

    private void handleRequest() {
        final Instant now = Instant.now();
        final int refillCount = (int) Math.floor(Duration.between(lastUpdate, now).toMillis() / refill.toMillis());
        
        value = Math.min(maximumBurst, value + refillCount * tokensPerRefill);
        // Only forward lastUpdate to the point where we've granted new elements
        final Instant grantEnd = lastUpdate.plus(refill.multipliedBy(refillCount));
        lastUpdate = now.isAfter(grantEnd) ? grantEnd : now;
        
        log.debug("Handling request for user {}, has {} tokens", self().path().name(), value);
        final boolean granted = value > 0;
        if (granted) {
            value--;
        }
        sender().tell(granted, self());
    }
    
    private void passivate() {
        context().parent().tell(new ShardRegion.Passivate(Stop.instance), self());
    }

    private static final class Stop {
        private static final Stop instance = new Stop();
    }
}
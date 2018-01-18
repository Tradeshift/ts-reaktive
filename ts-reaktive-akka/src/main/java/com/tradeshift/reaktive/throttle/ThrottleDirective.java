package com.tradeshift.reaktive.throttle;

import static akka.http.javadsl.server.Directives.onSuccess;
import static akka.http.javadsl.server.Directives.reject;
import static akka.http.javadsl.server.Directives.handleRejections;
import static akka.pattern.PatternsCS.ask;

import java.util.Base64;
import java.time.Duration;
import java.util.function.Supplier;
import java.nio.charset.StandardCharsets;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.cluster.sharding.ClusterSharding;
import akka.cluster.sharding.ClusterShardingSettings;
import akka.cluster.sharding.ShardRegion.MessageExtractor;
import akka.http.javadsl.server.Route;

/**
 * Implements an API rate limiting directive for akka http that is cluster-aware.
 */
public class ThrottleDirective {
    private final ActorRef shardRegion;

    /**
     * Returns a directive that can be used to throttle requests (API rate limiting). In an actual route, you have to invoke
     * {@link #throttle(String, Route)} to perform the rate check. You'll pass an application-specific key that identifies
     * the current user to that method, so that all users are limited individualing.
     * 
     * The throttling is cluster-aware, i.e. rate limits are applied across the whole akka cluster.
     * 
     * @param system Actor system to use (we use cluster sharding underneath to guarantee rate limiting across the cluster)
     * @param tokensPerRefill Number of tokens to grant on every refill period. Making a request uses up one token, if available, and fails otherwise.
     * @param refill Refill period. New tokens will be granted every period.
     * @param maximumBurst Maximum number of tokens that can be "saved up" if no requests are made for multiple periods.
     */
    public ThrottleDirective(ActorSystem system, int tokensPerRefill, Duration refill, int maximumBurst) {
        this.shardRegion = ClusterSharding.get(system).start("throttler-" + tokensPerRefill + "-" + refill.getSeconds() + "-" + maximumBurst,
            Props.create(ThrottleActor.class, () -> new ThrottleActor(tokensPerRefill, refill, maximumBurst)),
            ClusterShardingSettings.create(system), new MessageExtractor() {
                @Override
                public String entityId(Object message) {
                    return Base64.getEncoder().encodeToString(message.toString().getBytes(StandardCharsets.UTF_8));
                }

                @Override
                public Object entityMessage(Object message) {
                    return message;
                }

                @Override
                public String shardId(Object message) {
                    return String.valueOf(message.toString().hashCode() % 256);
                }
            });
    }

    /**
     * Runs the request through this directive's rate limiter under the given key, and rejects the request with
     * a {@link RateLimitExceededRejection} if the rate limit is exceeded.
     * 
     * @param key User-specific key to do rate limiting against. Each key is counted separately.
     * @param route Route to invoke if rate limit has not been exceeded.
     */
    public Route throttle(String key, Supplier<Route> route) {
        return onSuccess(() -> ask(shardRegion, key, 60000), granted -> {
            if (Boolean.class.cast(granted)) {
                return route.get();
            } else {
                return reject(RateLimitExceededRejection.instance);
            }
        });
    }
    
    /**
     * Invokes {@link #throttle(String, Route)} wrapped in {@link RateLimitExceededRejection}'s default
     * rejection handler (which fails the request with 420 Enhance Your Calm if rate limit is exceeded).
     * 
     * @param key User-specific key to do rate limiting against. Each key is counted separately.
     * @param route Route to invoke if rate limit has not been exceeded.
     */
    public Route throttleSealed(String key, Supplier<Route> route) {
        return handleRejections(RateLimitExceededRejection.defaultHandler, () -> throttle(key, route));
    }    
}

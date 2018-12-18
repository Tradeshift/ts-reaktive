package com.tradeshift.reaktive.singleton;

import static akka.actor.SupervisorStrategy.stop;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;

import akka.actor.Actor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.OneForOneStrategy;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.cluster.sharding.ClusterShardingGuardian.StartProxy;
import akka.cluster.singleton.ClusterSingletonManager;
import akka.cluster.singleton.ClusterSingletonManagerSettings;
import akka.cluster.singleton.ClusterSingletonProxy;
import akka.cluster.singleton.ClusterSingletonProxySettings;
import akka.japi.pf.DeciderBuilder;
import akka.pattern.Backoff;
import akka.pattern.BackoffSupervisor;
import io.vavr.collection.HashSet;
import io.vavr.collection.Set;
import scala.concurrent.duration.FiniteDuration;

/**
 * Wrapper class around a cluster singleton with backoff supervisor of a known type, that can both be
 * started and lookup up under a known global name.
 */
public class ClusterSingleton<T extends Actor> {
    private static Set<String> known = HashSet.empty();
    private static final Logger log = LoggerFactory.getLogger(ClusterSingleton.class);

    private final String name;
    private final Class<T> type;

    public ClusterSingleton(Class<T> type, String name) {
        this.type = type;
        if (known.contains(name)) {
            throw new IllegalArgumentException("Duplicate singleton name: " + name);
        }
        known = known.add(name);
        this.name = name;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ClusterSingleton<?> other = (ClusterSingleton<?>) obj;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        return true;
    }

    /**
     * Starts and returns the an {@link ActorRef} to the {@link ClusterSingletonManager} (and backoff supervisor)
     * that manage this actor singleton.
     *
     * Note that you can't send this ActorRef messages that should go to your actor: use {@link StartProxy} for that.
     *
     * @param constructor Constructor to pass to Props in order to actually create the actor
     */
    public ActorRef start(ActorSystem system, Supplier<T> constructor) {
        Config config = system.settings().config().getConfig("ts-reaktive.actors.singleton");
        Props backoffSupervisorProps = BackoffSupervisor.props(
            Backoff.onStop(
                Props.create(type, () -> constructor.get()), "actor",
                FiniteDuration.create(config.getDuration("min-backoff", SECONDS), SECONDS),
                FiniteDuration.create(config.getDuration("max-backoff", SECONDS), SECONDS),
                0.2
            ).withSupervisorStrategy(new OneForOneStrategy(
                DeciderBuilder .matchAny(e -> {
                    log.info("{}: Stopping and awaiting restart.", name, e);
                    return stop();
                })
                .build()
            ))
        );

        return system.actorOf(
            ClusterSingletonManager.props(backoffSupervisorProps,
                PoisonPill.getInstance(),
                ClusterSingletonManagerSettings.create(system)
            ), name);
    }

    /**
     * Returns a proxy that forwards messages to an already-running cluster singleton in an actor system.
     *
     * The proxy itself is not guaranteed to be a singleton, i.e. multiple invocations of this method will create
     * multiple proxy actors (which isn't a problem).
     */
    public ActorRef startProxy(ActorSystem system) {
        return system.actorOf(ClusterSingletonProxy.props("/user/" + name, ClusterSingletonProxySettings.create(system)));
    }
}

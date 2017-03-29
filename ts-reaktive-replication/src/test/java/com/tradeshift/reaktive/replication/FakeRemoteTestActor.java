package com.tradeshift.reaktive.replication;

import com.tradeshift.reaktive.replication.TestData.TestEvent;

import akka.japi.pf.ReceiveBuilder;
import akka.persistence.AbstractPersistentActor;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;

/**
 * Actor which just emits a single event into its journal and then exits. We do this to simulate a remote journal having been replicated to here.
 */
public class FakeRemoteTestActor extends AbstractPersistentActor {
    private final String event;

    public FakeRemoteTestActor(String event)
    {
        this.event = event;
        self().tell("init", self());
    }

    @Override
    public PartialFunction<Object, BoxedUnit> receiveCommand() {
        return ReceiveBuilder
            .matchEquals("init", msg -> {
                persist(TestEvent.newBuilder().setMsg(event).build(), e -> {
                    context().stop(self());
                });
            })
            .build();
    }

    @Override
    public PartialFunction<Object, BoxedUnit> receiveRecover() {
        return ReceiveBuilder
            .matchAny(msg -> {})
            .build();
    }

    @Override
    public String persistenceId() {
        return self().path().name();
    }

}

package com.tradeshift.reaktive.replication;

import akka.actor.AbstractExtensionId;
import akka.actor.ExtendedActorSystem;
import akka.actor.ExtensionIdProvider;

public class ReplicationId extends AbstractExtensionId<Replication> implements ExtensionIdProvider {
    public final static ReplicationId INSTANCE = new ReplicationId();

    private ReplicationId() {}

    public ReplicationId lookup() {
        return INSTANCE;
    }

    public Replication createExtension(ExtendedActorSystem system) {
        return new Replication(system);
    }
}
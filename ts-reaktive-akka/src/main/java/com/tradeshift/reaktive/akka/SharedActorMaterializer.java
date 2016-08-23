package com.tradeshift.reaktive.akka;

import akka.actor.AbstractExtensionId;
import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;
import akka.actor.Extension;
import akka.actor.ExtensionIdProvider;
import akka.stream.ActorMaterializer;

/**
 * Maintains a singleton actor materializer that can be shared and looked up amongst an actor system without the need
 * to pass it along constructors. 
 */
public class SharedActorMaterializer implements Extension {
    private static class Id extends AbstractExtensionId<SharedActorMaterializer> implements ExtensionIdProvider {
        private final static Id INSTANCE = new Id();

        private Id() {}

        public Id lookup() {
            return INSTANCE;
        }

        public SharedActorMaterializer createExtension(ExtendedActorSystem system) {
            return new SharedActorMaterializer(system);
        }
    }

    /**
     * Returns a shareable ActorMaterializer that will be the same for all callers for the same ActorSystem.
     */
    public static ActorMaterializer get(ActorSystem system) {
        return Id.INSTANCE.get(system).materializer;
    }

    private final ActorMaterializer materializer;
    
    private SharedActorMaterializer(ExtendedActorSystem system) {
        this.materializer = ActorMaterializer.create(system);
    }
}

package com.tradeshift.reaktive.actors.acl;

import java.util.UUID;

import io.vavr.Function1;
import io.vavr.collection.HashMap;
import io.vavr.control.Option;

/**
 * Helper class containing lambdas or method definitions which {@link ACL} uses when applying changes.
 * You typically don't have to use this class directly.
 */
public abstract class ACLBuilder<R,C> {
    /**
     * Creates a new, concrete, ACLBuilder based on lambda expressions.
     * @param getTargetId Function that yields a target UUID for a change (or none() if that change is to be ignored)
     * @param getGranted Function that yields a right that was granted for a change (or none() if the change doesn't grant rights)
     * @param getRevoked Function that yields a right that was revoked for a change (or none() if the change doesn't revoke rights)
     */
    public static <R,C> ACLBuilder<R,C> of(
        Function1<C, Option<UUID>> getTargetId,
        Function1<C, Option<R>> getGranted,
        Function1<C, Option<R>> getRevoked
    ) {
        return new ACLBuilder<R, C>() {
            @Override
            protected Option<UUID> getTargetId(C change) {
                return getTargetId.apply(change);
            }

            @Override
            protected Option<R> getGranted(C change) {
                return getGranted.apply(change);
            }

            @Override
            protected Option<R> getRevoked(C change) {
                return getRevoked.apply(change);
            }
        };
    }
    
    protected abstract Option<UUID> getTargetId(C change);
    protected abstract Option<R> getGranted(C change);
    protected abstract Option<R> getRevoked(C change);

    /**
     * Returns a new, empty, {@link ACL} which will use this ACLBuilder to resolve changes.
     */
    public ACL<R,C> empty() {
        return new ACL<>(this, HashMap.empty());
    }
}

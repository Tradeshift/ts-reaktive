package com.tradeshift.reaktive.actors.acl;

import java.util.UUID;

import javaslang.Function1;
import javaslang.Tuple;
import javaslang.collection.HashSet;
import javaslang.collection.Map;
import javaslang.collection.Set;
import javaslang.control.Option;

/**
 * Immutable class that manages a list of UUID entries that can have certain rights on a resource.
 * The UUIDs typically represent either users or user groups, but can be anything.
 * It can apply changes from a fixed change type C (typically another immutable data class), granting
 * and revoking rights.
 * 
 * @param R Enumeration containing the various rights that can be granted on the resource
 * @param C Type of the change object that can be applied to update the ACL
 */

public class ACL<R,C> {
    /**
     * Creates a new, empty, ACL, based on various lambda expressions for R and C.
     * @param getTargetId Function that yields a target UUID for a change (or none() if that change is to be ignored)
     * @param getGranted Function that yields a right that was granted for a change (or none() if the change doesn't grant rights)
     * @param getRevoked Function that yields a right that was revoked for a change (or none() if the change doesn't revoke rights)
     */
    public static <R,C> ACL<R,C> empty(
        Function1<C, Option<UUID>> getTargetId,
        Function1<C, Option<R>> getGranted,
        Function1<C, Option<R>> getRevoked
    ) {
        return ACLBuilder.of(getTargetId, getGranted, getRevoked).empty();
    }
    
    private final Map<R,Set<UUID>> entries;
    private final ACLBuilder<R,C> builder;

    protected ACL(ACLBuilder<R,C> builder, Map<R, Set<UUID>> entries) {
        this.entries = entries;
        this.builder = builder;
    }
        
    /**
     * Returns a new ACL with the given change applied.
     */
    public ACL<R,C> apply(C change) {
        for (UUID target: builder.getTargetId(change)) {
            Map<R,Set<UUID>> entries = this.entries;
            for (R granted: builder.getGranted(change)) {
                entries = entries.put(granted, entries.getOrElse(Tuple.of(granted, HashSet.empty()))._2.add(target));
            }
            for (R revoked: builder.getRevoked(change)) {
                entries = entries.put(revoked, entries.getOrElse(Tuple.of(revoked, HashSet.empty()))._2.remove(target));
            }
            return (entries.eq(this.entries)) ? this : new ACL<>(builder, entries);
        }
        
        return this;
    }

    /**
     * Returns whether the given target UUID is registered as having the given right (directly).
     * This function doesn't take any special "admin" rights into account.
     */
    public boolean isGranted(R right, UUID targetId) {
        return entries.get(right).filter(rights -> rights.contains(targetId)).isDefined();
    }
    
    /**
     * Returns whether the given targetId is the only (i.e. last) target that is granted that right (directly).
     */
    public boolean isOnlyGrantedTo(UUID targetId, R right) {
        return entries.get(right).contains(HashSet.of(targetId));
    }

    @SuppressWarnings("rawtypes")
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ACL)) return false;
        return entries.eq(((ACL)obj).entries);
    }
    
    @Override
    public int hashCode() {
        return entries.hashCode();
    }

    /**
     * Returns whether the ACL is empty, i.e. contains no entries at all.
     */
    public boolean isEmpty() {
        return entries.isEmpty();
    }
}

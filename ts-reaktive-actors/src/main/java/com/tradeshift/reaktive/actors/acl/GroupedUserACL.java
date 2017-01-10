package com.tradeshift.reaktive.actors.acl;

import java.util.List;
import java.util.UUID;

import com.tradeshift.reaktive.protobuf.UUIDs;

import javaslang.Function1;
import javaslang.collection.HashSet;
import javaslang.collection.Set;
import javaslang.control.Option;

/**
 * Combines an ACL for users and an ACL for groups.
 * 
 * This class implements a security mechanism where during security checks, an application is expected
 * to have the current users' ID, and all of it group memberships, cached in memory.
 * 
 * The class is aware of a special "admin" right. Users that have that right implicitly have all other rights as well.
 *
 * @param R Enumeration containing the various rights that can be granted on the resource
 * @param C Type of the change object that can be applied to update the ACL
 */
public class GroupedUserACL<R extends Enum<R>,C> {
    /**
     * Creates a new, empty, ACL, based on various lambda expressions for R and C.
     * @param admin Enumeration literal that is the special "admin" right for this ACL.
     * @param getUserId Function that yields a target user UUID for a change (or none() if that change doesn't target a user)
     * @param getUserGroupId Function that yields a target user group UUID for a change (or none() if that change doesn't target a user group)
     * @param getGranted Function that yields a right that was granted for a change (or none() if the change doesn't grant rights)
     * @param getRevoked Function that yields a right that was revoked for a change (or none() if the change doesn't revoke rights)
     */
    public static <R extends Enum<R>,C> GroupedUserACL<R,C> empty(
        R admin,
        Function1<C, Option<UUID>> getUserId,
        Function1<C, Option<UUID>> getUserGroupId,
        Function1<C, Option<R>> getGranted,
        Function1<C, Option<R>> getRevoked
    ) {
        return new GroupedUserACL<>(admin, ACLBuilder.of(getUserId, getGranted, getRevoked).empty(), ACLBuilder.of(getUserGroupId, getGranted, getRevoked).empty());
    }
    
    private final R admin;
    private final ACL<R,C> userAcl;
    private final ACL<R,C> groupAcl;
    
    private GroupedUserACL(R admin, ACL<R, C> userAcl, ACL<R, C> groupAcl) {
        this.admin = admin;
        this.userAcl = userAcl;
        this.groupAcl = groupAcl;
    }
    
    /**
     * Returns a new ACL with the given change applied.
     */
    public GroupedUserACL<R,C> apply(C change) {
        return new GroupedUserACL<>(admin, userAcl.apply(change), groupAcl.apply(change));
    }
    
    /**
     * Returns whether a certain user, being a member of certain groups, can perform a certain right.
     * If the user (or one of its groups) has the special "admin" right, this function always returns true.
     * @param user The user to check
     * @param userGroups Groups the user is in (in protobuf format, since that's typically where clustered apps will maintain them)
     * @param right The right to check
     * @return whether the user, or one of its groups, can perform that right
     */
    public boolean isAllowed(UUID user, List<com.tradeshift.reaktive.protobuf.Types.UUID> userGroups, R right) {
        if (userAcl.isAllowed(right, user) ||
            userGroups.stream().map(UUIDs::toJava).anyMatch(group -> groupAcl.isAllowed(right, group))) {
            return true;
        } else if (right != admin) {
            return isAllowed(user, userGroups, admin);
        } else {
            return false;
        }
    }
    
    /**
     * Returns all rights that a certain user, being a member of certain groups, has.
     * If the user (or one of its groups) has the special "admin" right, this function always returns the complete set of rights.
     * @param user The user to check
     * @param userGroups Groups the user is in (in protobuf format, since that's typically where clustered apps will maintain them)
     * @return all rights that the user, and all of its groups, have
     */
    public Set<R> getRights(UUID userId, List<com.tradeshift.reaktive.protobuf.Types.UUID> userGroups) {
        Set<R> set = HashSet.empty();
        for (R right: admin.getDeclaringClass().getEnumConstants()) {
            if (isAllowed(userId, userGroups, right)) {
                set = set.add(right);
            }
        }
        return set;
    }
    
    /**
     * Returns whether the ACL is empty, i.e. contains no entries at all.
     */
    public boolean isEmpty() {
        return userAcl.isEmpty() && groupAcl.isEmpty();
    }
}

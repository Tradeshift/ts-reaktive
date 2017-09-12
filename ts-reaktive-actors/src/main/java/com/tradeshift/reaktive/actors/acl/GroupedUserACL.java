package com.tradeshift.reaktive.actors.acl;

import java.util.List;
import java.util.UUID;

import com.tradeshift.reaktive.protobuf.UUIDs;

import io.vavr.Function1;
import io.vavr.collection.HashSet;
import io.vavr.collection.Set;
import io.vavr.control.Option;

/**
 * Combines an ACL for users and an ACL for groups.
 * 
 * This class implements a security mechanism where during security checks, an application is expected
 * to have the current users' ID, and all of it group memberships, cached in memory.
 *
 * @param R Enumeration containing the various rights that can be granted on the resource
 * @param C Type of the change object that can be applied to update the ACL
 */
public class GroupedUserACL<R extends Enum<R>,C> {
    /**
     * Creates a new, empty, ACL, based on various lambda expressions for R and C.
     * @param rightsType The type of enum for which this ACL checks access rights
     * @param getUserId Function that yields a target user UUID for a change (or none() if that change doesn't target a user)
     * @param getUserGroupId Function that yields a target user group UUID for a change (or none() if that change doesn't target a user group)
     * @param getGranted Function that yields a right that was granted for a change (or none() if the change doesn't grant rights)
     * @param getRevoked Function that yields a right that was revoked for a change (or none() if the change doesn't revoke rights)
     */
    public static <R extends Enum<R>,C> GroupedUserACL<R,C> empty(
        Class<R> rightsType,
        Function1<C, Option<UUID>> getUserId,
        Function1<C, Option<UUID>> getUserGroupId,
        Function1<C, Option<R>> getGranted,
        Function1<C, Option<R>> getRevoked
    ) {
        return new GroupedUserACL<>(rightsType, ACLBuilder.of(getUserId, getGranted, getRevoked).empty(), ACLBuilder.of(getUserGroupId, getGranted, getRevoked).empty());
    }
    
    private final Class<R> rightsType;
    private final ACL<R,C> userAcl;
    private final ACL<R,C> groupAcl;
    
    private GroupedUserACL(Class<R> rightsType, ACL<R, C> userAcl, ACL<R, C> groupAcl) {
        this.rightsType = rightsType;
        this.userAcl = userAcl;
        this.groupAcl = groupAcl;
    }
    
    /**
     * Returns a new ACL with the given change applied.
     */
    public GroupedUserACL<R,C> apply(C change) {
        return new GroupedUserACL<>(rightsType, userAcl.apply(change), groupAcl.apply(change));
    }
    
    /**
     * Returns whether a certain user, being a member of certain groups, can perform a certain right.
     * @param user The user to check
     * @param userGroups Groups the user is in (in protobuf format, since that's typically where clustered apps will maintain them)
     * @param right The right to check
     * @return whether the user, or one of its groups, can perform that right
     */
    public boolean isGranted(UUID user, List<com.tradeshift.reaktive.protobuf.Types.UUID> userGroups, R right) {
        return (userAcl.isGranted(right, user) ||
                userGroups.stream().map(UUIDs::toJava).anyMatch(group -> groupAcl.isGranted(right, group)));
    }
    
    /**
     * Returns whether a certain user is directly granted a certain right (not taking user groups into account).
     */
    public boolean isGrantedToUser(UUID user, R right) {
        return userAcl.isGranted(right, user);
    }
    
    /**
     * Returns whether a certain user group is directly granted a certain right.
     */
    public boolean isGrantedToGroup(UUID userGroup, R right) {
        return groupAcl.isGranted(right, userGroup);
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
        for (R right: rightsType.getEnumConstants()) {
            if (isGranted(userId, userGroups, right)) {
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
    
    /**
     * Returns whether the given userId is the only (i.e. last) user that is granted that right (directly).
     */
    public boolean isOnlyGrantedToUser(UUID userId, R right) {
        return userAcl.isOnlyGrantedTo(userId, right);
    }

    /**
     * Returns whether the given groupId is the only (i.e. last) group that is granted that right (directly).
     */
    public boolean isOnlyGrantedToGroup(UUID groupId, R right) {
        return groupAcl.isOnlyGrantedTo(groupId, right);
    }
}


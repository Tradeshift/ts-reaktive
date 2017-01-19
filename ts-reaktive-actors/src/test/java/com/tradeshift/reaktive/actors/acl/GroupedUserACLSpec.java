package com.tradeshift.reaktive.actors.acl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.cuppa.Cuppa.beforeEach;
import static com.tradeshift.reaktive.protobuf.UUIDs.toProtobuf;
import static javaslang.control.Option.*;
import static org.forgerock.cuppa.Cuppa.describe;
import static org.forgerock.cuppa.Cuppa.it;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.forgerock.cuppa.junit.CuppaRunner;
import org.junit.runner.RunWith;

import javaslang.control.Option;

@RunWith(CuppaRunner.class)
public class GroupedUserACLSpec {
    private static enum Right {
        ADMIN, READ, WRITE
    }
    private static class Change {
        private final Option<UUID> userId;
        private final Option<UUID> userGroupId;
        private final Option<Right> granted;
        private final Option<Right> revoked;
        
        public Change(Option<UUID> userId, Option<UUID> userGroupId, Option<Right> granted, Option<Right> revoked) {
            this.userId = userId;
            this.userGroupId = userGroupId;
            this.granted = granted;
            this.revoked = revoked;
        }
        
        public Option<Right> getGranted() {
            return granted;
        }
        
        public Option<Right> getRevoked() {
            return revoked;
        }
        
        public Option<UUID> getUserId() {
            return userId;
        }
        
        public Option<UUID> getUserGroupId() {
            return userGroupId;
        }
    }
    
    GroupedUserACL<Right,Change> acl;
    {
        describe("A GroupedUserACL that defines a special admin right", () -> {
            UUID userId = UUID.fromString("7dd3ea2d-e922-43b6-b183-82e271546692");
            UUID groupId = UUID.fromString("f4aafd4b-f31f-479c-87d0-80b9fc3e610e");
            List<com.tradeshift.reaktive.protobuf.Types.UUID> groups = Collections.singletonList(toProtobuf(groupId));
            
            beforeEach(() -> acl = GroupedUserACL.empty(Right.ADMIN, Change::getUserId, Change::getUserGroupId, Change::getGranted, Change::getRevoked));
            
            it("should grant access when a direct user UUID is given a direct right", () -> {
                GroupedUserACL<Right, Change> updated = acl.apply(new Change(some(userId), none(), some(Right.WRITE), none()));
                assertThat(updated.isAllowed(userId, Collections.emptyList(), Right.WRITE)).isTrue();
                assertThat(updated.isAllowed(userId, Collections.emptyList(), Right.READ)).isFalse();
                assertThat(updated.isAllowed(userId, Collections.emptyList(), Right.ADMIN)).isFalse();
                assertThat(updated.getRights(userId, Collections.emptyList())).containsOnly(Right.WRITE);
            });
            
            it("should grant access when a user's group UUID is given a direct right", () -> {
                GroupedUserACL<Right, Change> updated = acl.apply(new Change(none(), some(groupId), some(Right.WRITE), none()));
                assertThat(updated.isAllowed(userId, groups, Right.WRITE)).isTrue();
                assertThat(updated.isAllowed(userId, groups, Right.READ)).isFalse();
                assertThat(updated.isAllowed(userId, groups, Right.ADMIN)).isFalse();
                assertThat(updated.getRights(userId, groups)).containsOnly(Right.WRITE);
            });
            
            it("should grant all access when a direct user UUID is given admin rights", () -> {
                GroupedUserACL<Right, Change> updated = acl.apply(new Change(some(userId), none(), some(Right.ADMIN), none()));
                assertThat(updated.isAllowed(userId, Collections.emptyList(), Right.WRITE)).isTrue();
                assertThat(updated.isAllowed(userId, Collections.emptyList(), Right.READ)).isTrue();
                assertThat(updated.isAllowed(userId, Collections.emptyList(), Right.ADMIN)).isTrue();
                assertThat(updated.getRights(userId, Collections.emptyList())).containsOnly(Right.READ, Right.WRITE, Right.ADMIN);
            });
            
            it("should grant all access when a user's group UUID is given admin rights", () -> {
                GroupedUserACL<Right, Change> updated = acl.apply(new Change(none(), some(groupId), some(Right.ADMIN), none()));
                assertThat(updated.isAllowed(userId, groups, Right.WRITE)).isTrue();
                assertThat(updated.isAllowed(userId, groups, Right.READ)).isTrue();
                assertThat(updated.isAllowed(userId, groups, Right.ADMIN)).isTrue();
                assertThat(updated.getRights(userId, groups)).containsOnly(Right.READ, Right.WRITE, Right.ADMIN);
            });
        });
        
        describe("A GroupedUserACL without special admin right", () -> {
            UUID userId = UUID.fromString("7dd3ea2d-e922-43b6-b183-82e271546692");
            
            beforeEach(() -> acl = GroupedUserACL.empty(Right.class, Change::getUserId, Change::getUserGroupId, Change::getGranted, Change::getRevoked));
            
            it("should not grant further access when a direct user UUID is given admin rights", () -> {
                GroupedUserACL<Right, Change> updated = acl.apply(new Change(some(userId), none(), some(Right.ADMIN), none()));
                assertThat(updated.isAllowed(userId, Collections.emptyList(), Right.WRITE)).isFalse();
                assertThat(updated.isAllowed(userId, Collections.emptyList(), Right.READ)).isFalse();
                assertThat(updated.isAllowed(userId, Collections.emptyList(), Right.ADMIN)).isTrue();
                assertThat(updated.getRights(userId, Collections.emptyList())).containsOnly(Right.ADMIN);
            });
        });
    }
}

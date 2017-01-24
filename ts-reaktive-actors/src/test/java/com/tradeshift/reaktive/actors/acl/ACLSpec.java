package com.tradeshift.reaktive.actors.acl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.cuppa.Cuppa.beforeEach;
import static org.forgerock.cuppa.Cuppa.describe;
import static org.forgerock.cuppa.Cuppa.it;

import java.util.UUID;

import org.forgerock.cuppa.junit.CuppaRunner;
import org.junit.runner.RunWith;

import javaslang.control.Option;
import static javaslang.control.Option.*;

@RunWith(CuppaRunner.class)
public class ACLSpec {
    private static enum Right {
        ADMIN, READ, WRITE
    }
    private static class Change {
        private final Option<UUID> userId;
        private final Option<Right> granted;
        private final Option<Right> revoked;
        
        public Change(Option<UUID> userId, Option<Right> granted, Option<Right> revoked) {
            this.userId = userId;
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
    }
    
    ACL<Right,Change> acl;
    {
        describe("ACL", () -> {
            UUID userId = UUID.fromString("7dd3ea2d-e922-43b6-b183-82e271546692");
            
            beforeEach(() -> acl = ACL.empty(Change::getUserId, Change::getGranted, Change::getRevoked));
            
            it("should not apply a change for which getTargetId returns none()", () -> {
                assertThat(
                    acl.apply(new Change(none(), none(), none()))
                ).isSameAs(acl);
            });
            
            it("should not apply a change for which both getGranted and getRevoked return none()", () -> {
                assertThat(
                    acl.apply(new Change(some(userId), none(), none()))
                ).isSameAs(acl);
            });
            
            it("should grant a right returned by getGranted()", () -> {
                ACL<Right, Change> updated = acl
                    .apply(new Change(some(userId), some(Right.WRITE), none()));
                assertThat(updated.isGranted(Right.WRITE, userId)).isTrue();
                assertThat(updated.isGranted(Right.READ, userId)).isFalse();
                assertThat(updated.isGranted(Right.ADMIN, userId)).isFalse();
                assertThat(updated.isGranted(Right.WRITE, UUID.randomUUID())).isFalse();
            });
            
            it("should not treat ADMIN in any special way", () -> {
                ACL<Right, Change> updated = acl
                    .apply(new Change(some(userId), some(Right.ADMIN), none()));
                assertThat(updated.isGranted(Right.WRITE, userId)).isFalse();
                assertThat(updated.isGranted(Right.READ, userId)).isFalse();
                assertThat(updated.isGranted(Right.ADMIN, userId)).isTrue();
            });
            
            it("should revoke a right returned by getRevoked()", () -> {
                ACL<Right, Change> updated = acl
                    .apply(new Change(some(userId), some(Right.WRITE), none()))
                    .apply(new Change(some(userId), none(), some(Right.WRITE)));
                assertThat(updated.isGranted(Right.WRITE, userId)).isFalse();
            });
            
            it("should be equal to another ACL having seen the same changes", () -> {
                assertThat(
                    acl.apply(new Change(some(userId), some(Right.WRITE), none()))
                ).isEqualTo(
                    acl.apply(new Change(some(userId), some(Right.WRITE), none()))
                );
            });
            
            it("should update appropriately if rights are both granted and revoked", () -> {
                ACL<Right, Change> updated = acl
                    .apply(new Change(some(userId), some(Right.WRITE), none()))            // grant write
                    .apply(new Change(some(userId), some(Right.READ), some(Right.WRITE))); // grant read, revoke write
                assertThat(updated.isGranted(Right.WRITE, userId)).isFalse();
                assertThat(updated.isGranted(Right.READ, userId)).isTrue();
            });
            
            it("should return the same instance when granting a right already present", () -> {
                ACL<Right, Change> updated = acl
                    .apply(new Change(some(userId), some(Right.WRITE), none()));
                assertThat(updated
                    .apply(new Change(some(userId), some(Right.WRITE), none()))
                ).isSameAs(updated);
            });
            
            it("should keep track of the last/only user granted a particular right", () -> {
                UUID otherUserId = UUID.fromString("2204f471-afb4-411b-bcbc-f76c2cbb69ea");
                
                assertThat(acl.isOnlyGrantedTo(userId, Right.WRITE)).isFalse();
                ACL<Right, Change> updated;
                
                updated = acl.apply(new Change(some(userId), some(Right.WRITE), none()));
                assertThat(updated.isOnlyGrantedTo(userId, Right.WRITE)).isTrue();
                
                updated = updated.apply(new Change(some(otherUserId), some(Right.WRITE), none()));
                assertThat(updated.isOnlyGrantedTo(userId, Right.WRITE)).isFalse();
                
                updated = updated.apply(new Change(some(userId), none(), some(Right.WRITE)));
                assertThat(updated.isOnlyGrantedTo(otherUserId, Right.WRITE)).isTrue();
            });
        });
    }
}

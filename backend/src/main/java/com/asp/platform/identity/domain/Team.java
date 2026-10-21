package com.asp.platform.identity.domain;

import java.util.List;
import java.util.UUID;

import com.asp.platform.shared.Role;

/// Everyone who works in one workshop, with the rules for changing roles and access.
public final class Team {

    private final List<User> members;

    public Team(List<User> members) {
        this.members = List.copyOf(members);
    }

    /// Changes a member's role and whether they may sign in.
    ///
    /// @param actorId the owner making the change
    /// @return the member as changed, still to be saved
    /// @throws UserNotFoundException     when the member is not in this team
    /// @throws OwnAccountChangeException when the actor changes their own role or deactivates themselves
    /// @throws LastOwnerException        when no active owner would be left
    public User changeAccess(UUID actorId, UUID memberId, Role role, boolean active) {
        User member = members.stream()
                .filter(candidate -> candidate.id().equals(memberId))
                .findFirst()
                .orElseThrow(UserNotFoundException::new);
        if (member.id().equals(actorId) && (member.role() != role || member.active() != active)) {
            throw new OwnAccountChangeException();
        }
        User changed = member.withAccess(role, active);
        boolean ownerLeft = members.stream()
                .map(candidate -> candidate.id().equals(memberId) ? changed : candidate)
                .anyMatch(User::isActiveOwner);
        if (!ownerLeft) {
            throw new LastOwnerException();
        }
        return changed;
    }
}

package com.asp.platform.identity.api;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.asp.platform.identity.application.Invitation;

/// A [UserResponse] plus the user's activation link, which is shown only in this response.
record InvitationResponse(UUID id, @Nullable UUID tenantId, String name, String email, String role, boolean active,
        boolean pending, String activationPath, Instant activationExpiresAt) {

    static InvitationResponse of(Invitation invitation) {
        UserResponse u = UserResponse.of(invitation.user());
        return new InvitationResponse(u.id(), u.tenantId(), u.name(), u.email(), u.role(), u.active(), u.pending(),
                invitation.link().path(), invitation.link().expiresAt());
    }
}

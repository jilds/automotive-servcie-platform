package com.asp.platform.identity.application;

import com.asp.platform.identity.domain.User;

/// A user who was just created or re-invited, with the activation link to hand them.
public record Invitation(User user, ActivationLink link) {
}

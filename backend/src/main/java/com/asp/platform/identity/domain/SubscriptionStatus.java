package com.asp.platform.identity.domain;

/// Where a workshop's subscription stands. Only trial and active workshops may sign in.
public enum SubscriptionStatus {
    TRIAL,
    ACTIVE,
    SUSPENDED,
    CANCELLED;

    /// True when the workshop's users may sign in and use their tokens.
    public boolean allowsSignIn() {
        return this == TRIAL || this == ACTIVE;
    }
}

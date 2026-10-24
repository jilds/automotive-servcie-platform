package com.asp.platform.identity.domain;

import com.asp.platform.shared.ConflictException;

/// The activation link is older than 72 hours; the owner or platform admin must issue a new one.
public class ActivationExpiredException extends ConflictException {

    public ActivationExpiredException() {
        super("activation-expired", "This activation link has expired");
    }
}

package com.asp.platform.identity.domain;

import com.asp.platform.shared.ConflictException;

/// Owners cannot change their own role or deactivate themselves, so nobody locks themselves out by mistake.
public class OwnAccountChangeException extends ConflictException {

    public OwnAccountChangeException() {
        super("own-account-change", "You cannot change your own role or deactivate yourself");
    }
}

package com.asp.platform.identity.domain;

import com.asp.platform.shared.ConflictException;

/// The user has set their password already, so the owner cannot issue them an activation link.
public class AlreadyActivatedException extends ConflictException {

    public AlreadyActivatedException() {
        super("already-activated", "This user has already activated their account");
    }
}

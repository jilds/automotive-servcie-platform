package com.asp.platform.identity.domain;

import com.asp.platform.shared.NotFoundException;

/// The activation link is unknown, was already used, or its user can no longer sign in.
public class ActivationInvalidException extends NotFoundException {

    public ActivationInvalidException() {
        super("activation-invalid", "This activation link is not valid");
    }
}

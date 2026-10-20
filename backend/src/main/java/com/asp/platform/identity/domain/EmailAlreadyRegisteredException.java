package com.asp.platform.identity.domain;

import com.asp.platform.shared.ConflictException;

public class EmailAlreadyRegisteredException extends ConflictException {

    public EmailAlreadyRegisteredException() {
        super("email-taken", "Email already registered");
    }
}

package com.asp.platform.identity.domain;

import com.asp.platform.shared.ConflictException;

/// Another workshop already uses that branded login address.
public class SlugTakenException extends ConflictException {

    public SlugTakenException() {
        super("slug-taken", "Another workshop already uses this address");
    }
}

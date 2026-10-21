package com.asp.platform.identity.domain;

import com.asp.platform.shared.ConflictException;

public class LastOwnerException extends ConflictException {

    public LastOwnerException() {
        super("last-owner", "The workshop must keep at least one active owner");
    }
}

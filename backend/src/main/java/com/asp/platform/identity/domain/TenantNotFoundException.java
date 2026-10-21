package com.asp.platform.identity.domain;

import com.asp.platform.shared.NotFoundException;

public class TenantNotFoundException extends NotFoundException {

    public TenantNotFoundException() {
        super("workshop-not-found", "Workshop not found");
    }
}

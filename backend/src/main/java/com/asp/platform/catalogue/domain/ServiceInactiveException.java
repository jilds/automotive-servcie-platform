package com.asp.platform.catalogue.domain;

import com.asp.platform.shared.ConflictException;

/// An inactive service cannot be added to a work order or a package. Mapped to 409 `service-inactive`.
public class ServiceInactiveException extends ConflictException {

    public ServiceInactiveException() {
        super("service-inactive", "This service is no longer offered");
    }
}

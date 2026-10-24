package com.asp.platform.catalogue.domain;

import com.asp.platform.shared.NotFoundException;

public class ServiceNotFoundException extends NotFoundException {

    public ServiceNotFoundException() {
        super("service-not-found", "Service not found");
    }
}

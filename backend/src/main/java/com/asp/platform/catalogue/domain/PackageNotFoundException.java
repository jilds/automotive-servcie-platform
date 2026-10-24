package com.asp.platform.catalogue.domain;

import com.asp.platform.shared.NotFoundException;

public class PackageNotFoundException extends NotFoundException {

    public PackageNotFoundException() {
        super("package-not-found", "Package not found");
    }
}

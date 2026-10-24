package com.asp.platform.catalogue.domain;

import com.asp.platform.shared.ConflictException;

/// An inactive package cannot be added to a work order. Mapped to 409 `package-inactive`.
public class PackageInactiveException extends ConflictException {

    public PackageInactiveException() {
        super("package-inactive", "This package is no longer offered");
    }
}

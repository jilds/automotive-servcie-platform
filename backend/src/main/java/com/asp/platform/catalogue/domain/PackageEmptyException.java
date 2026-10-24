package com.asp.platform.catalogue.domain;

import com.asp.platform.shared.InvalidInputException;

/// A package needs at least one service or product. Mapped to 400 `package-empty` on the field `items`.
public class PackageEmptyException extends InvalidInputException {

    public PackageEmptyException() {
        super("package-empty", "items", "A package needs at least one service or product");
    }
}

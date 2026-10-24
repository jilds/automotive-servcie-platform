package com.asp.platform.stock.domain;

import com.asp.platform.shared.ConflictException;

/// Another product of the workshop already has this SKU (case ignored). Mapped to 409 `duplicate-sku`.
public class DuplicateSkuException extends ConflictException {

    public DuplicateSkuException(String sku) {
        super("duplicate-sku", "A product with SKU " + sku + " already exists");
    }
}

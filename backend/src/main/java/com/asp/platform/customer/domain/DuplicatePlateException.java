package com.asp.platform.customer.domain;

import com.asp.platform.shared.ConflictException;

public class DuplicatePlateException extends ConflictException {

    public DuplicatePlateException(String plate) {
        super("duplicate-plate", "A vehicle with plate " + plate + " already exists");
    }
}

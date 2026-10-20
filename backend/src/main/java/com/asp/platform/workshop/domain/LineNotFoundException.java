package com.asp.platform.workshop.domain;

import com.asp.platform.shared.NotFoundException;

public class LineNotFoundException extends NotFoundException {

    public LineNotFoundException() {
        super("line-not-found", "Line not found");
    }
}

package com.asp.platform.shared;

/// A public endpoint of an add-on (online booking, online shop) the workshop does not have switched on. A 404 like an
/// unknown workshop, so the answer tells nothing about the workshop's subscription (ADR 0004).
public class AddOnNotAvailableException extends NotFoundException {

    public static final String CODE = "add-on-not-available";

    public AddOnNotAvailableException() {
        super(CODE, "This service is not available for this workshop");
    }
}

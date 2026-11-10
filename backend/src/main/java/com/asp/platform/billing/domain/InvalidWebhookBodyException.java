package com.asp.platform.billing.domain;

import com.asp.platform.shared.InvalidInputException;

/// A correctly signed webhook whose body cannot be read. 400 validation.
public class InvalidWebhookBodyException extends InvalidInputException {

    public InvalidWebhookBodyException() {
        super("validation", "body", "The webhook body is not valid");
    }
}

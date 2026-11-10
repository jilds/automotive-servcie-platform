package com.asp.platform.billing.domain;

/// The webhook body is larger than the limit; it is refused without reading further or checking the signature.
/// Mapped to 413 webhook-too-large.
public class WebhookTooLargeException extends RuntimeException {

    public static final String CODE = "webhook-too-large";

    public WebhookTooLargeException() {
        super("The webhook body is too large");
    }
}

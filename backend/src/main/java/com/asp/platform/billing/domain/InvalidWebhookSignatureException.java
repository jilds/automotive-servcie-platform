package com.asp.platform.billing.domain;

/// The webhook is not signed by the provider, or is too old. Mapped to 401 webhook-signature-invalid.
public class InvalidWebhookSignatureException extends RuntimeException {

    public static final String CODE = "webhook-signature-invalid";

    public InvalidWebhookSignatureException() {
        super("Invalid webhook signature");
    }
}

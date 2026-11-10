package com.asp.platform.billing.domain;

import com.asp.platform.shared.NotFoundException;

/// The webhook names a provider that is not the configured one. 404.
public class UnknownPaymentProviderException extends NotFoundException {

    public UnknownPaymentProviderException() {
        super("unknown-payment-provider", "Unknown payment provider");
    }
}

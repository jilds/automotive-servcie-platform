package com.asp.platform.billing.domain;

import com.asp.platform.shared.ConflictException;

/// The provider needed for this operation is set to none. 409 provider-not-configured.
public class ProviderNotConfiguredException extends ConflictException {

    public ProviderNotConfiguredException() {
        super("provider-not-configured", "No provider is configured for this operation");
    }
}

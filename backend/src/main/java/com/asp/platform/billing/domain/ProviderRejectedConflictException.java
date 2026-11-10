package com.asp.platform.billing.domain;

import com.asp.platform.shared.ConflictException;

/// The invoicing provider refused a void. 409.
public class ProviderRejectedConflictException extends ConflictException {

    public ProviderRejectedConflictException() {
        super("provider-rejected", "The provider refused the request");
    }
}

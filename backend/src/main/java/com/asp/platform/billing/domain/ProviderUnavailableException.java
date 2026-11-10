package com.asp.platform.billing.domain;

/// The provider could not be reached or did not answer in time: the outcome is unknown. Nothing is known to have been
/// done. Mapped to 503 provider-unavailable where the caller cannot store the request; stored as PENDING otherwise.
public class ProviderUnavailableException extends RuntimeException {

    public static final String CODE = "provider-unavailable";

    public ProviderUnavailableException(String message) {
        super(message);
    }
}

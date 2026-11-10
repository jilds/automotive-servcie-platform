package com.asp.platform.billing.domain;

/// The provider understood the request and refused it. Definitive: asking again changes nothing.
public class ProviderRejectedException extends RuntimeException {

    private final String code;

    /// @param code    short machine code of the provider, stored with the failed document
    /// @param message what the provider said, in English
    public ProviderRejectedException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}

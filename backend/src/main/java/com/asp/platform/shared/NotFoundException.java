package com.asp.platform.shared;

/// Base for "this thing does not exist (in your workshop)" errors. Mapped to 404.
public abstract class NotFoundException extends RuntimeException {

    private final String code;

    /// @param code    stable, kebab-case error code the UI translates, for example "duplicate-plate"
    /// @param message English fallback shown when the UI has no translation for the code
    protected NotFoundException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}

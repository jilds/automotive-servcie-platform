package com.asp.platform.shared;

/// Base for errors where the request clashes with the current state, such as a duplicate plate. Mapped to 409.
public abstract class ConflictException extends RuntimeException {

    private final String code;

    /// @param code    stable, kebab-case error code the UI translates, for example "duplicate-plate"
    /// @param message English fallback shown when the UI has no translation for the code
    protected ConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}

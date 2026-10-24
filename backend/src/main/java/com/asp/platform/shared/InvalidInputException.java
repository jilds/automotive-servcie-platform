package com.asp.platform.shared;

/// Base for a domain rule that rejects one input field, such as an invalid NIF. Mapped to 400 with the code and an
/// `errors` map holding the field, like a validation error, so the UI can show it next to the input.
public abstract class InvalidInputException extends RuntimeException {

    private final String code;
    private final String field;

    /// @param code    stable, kebab-case error code the UI translates, for example "invalid-nif"
    /// @param field   request field the error belongs to, dotted for nested fields ("address.postalCode")
    /// @param message English fallback shown when the UI has no translation for the code
    protected InvalidInputException(String code, String field, String message) {
        super(message);
        this.code = code;
        this.field = field;
    }

    public String code() {
        return code;
    }

    public String field() {
        return field;
    }
}

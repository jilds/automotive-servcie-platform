package com.asp.platform.workshop.domain;

/// The user picked as mechanic is not an active mechanic of this workshop: another workshop's user, a
/// deactivated one, or someone with another role.
public class InvalidMechanicException extends RuntimeException {

    public static final String CODE = "invalid-mechanic";

    private final String field;

    /// @param field the request field that held the mechanic id, so the UI can mark it
    public InvalidMechanicException(String field) {
        super("The assigned user is not an active mechanic of this workshop");
        this.field = field;
    }

    public String field() {
        return field;
    }
}

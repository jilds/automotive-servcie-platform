package com.asp.platform.shared;

/// A Portuguese NIF with a wrong check digit or first digit. Mapped to 400 `invalid-nif` on the field `nif`.
public class InvalidNifException extends InvalidInputException {

    public static final String CODE = "invalid-nif";

    public InvalidNifException() {
        super(CODE, "nif", "Not a valid Portuguese NIF");
    }
}

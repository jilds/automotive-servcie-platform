package com.asp.platform.identity.domain;

/// Wrong email or password. The message never says which one.
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}

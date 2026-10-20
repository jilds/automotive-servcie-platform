package com.asp.shared.web;

/** The request clashes with the current state, such as a duplicate plate or an invalid status change. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}

package com.asp.platform.identity.application;

/// What a successful login or activation returns.
public record Session(IssuedToken token, Account account) {
}

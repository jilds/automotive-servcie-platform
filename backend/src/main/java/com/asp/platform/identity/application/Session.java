package com.asp.platform.identity.application;

import com.asp.platform.identity.domain.User;

/// What a successful signup or login returns.
public record Session(IssuedToken token, User user) {
}

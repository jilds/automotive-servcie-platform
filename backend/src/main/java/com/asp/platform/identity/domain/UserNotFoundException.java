package com.asp.platform.identity.domain;

import com.asp.platform.shared.NotFoundException;

public class UserNotFoundException extends NotFoundException {

    public UserNotFoundException() {
        super("user-not-found", "User not found");
    }
}

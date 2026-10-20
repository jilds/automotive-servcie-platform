package com.asp.platform.identity.application;

import org.jspecify.annotations.Nullable;

public record SignupCommand(String workshopName, @Nullable String nif, String ownerName, String email,
        String password) {
}

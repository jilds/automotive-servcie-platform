package com.asp.platform.identity.api;

import java.time.Instant;

import com.asp.platform.identity.application.ActivationLink;

/// @param activationPath `/activate?token=…`, relative to the app's address; shown once, the server keeps a hash
record ActivationLinkResponse(String activationPath, Instant expiresAt) {

    static ActivationLinkResponse of(ActivationLink link) {
        return new ActivationLinkResponse(link.path(), link.expiresAt());
    }
}

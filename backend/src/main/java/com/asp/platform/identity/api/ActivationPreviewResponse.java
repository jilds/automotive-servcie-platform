package com.asp.platform.identity.api;

import java.time.Instant;

import com.asp.platform.identity.application.ActivationPreview;

record ActivationPreviewResponse(String email, String name, String workshopName, Instant expiresAt) {

    static ActivationPreviewResponse of(ActivationPreview p) {
        return new ActivationPreviewResponse(p.email(), p.name(), p.workshopName(), p.expiresAt());
    }
}

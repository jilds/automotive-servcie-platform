package com.asp.platform.identity.api;

import java.time.Instant;

import com.asp.platform.identity.application.ProvisionedWorkshop;

/// @param activationPath the owner's one-time link, shown only in this response
record ProvisionedWorkshopResponse(AdminWorkshopResponse workshop, String activationPath, Instant activationExpiresAt) {

    static ProvisionedWorkshopResponse of(ProvisionedWorkshop p, boolean expired) {
        return new ProvisionedWorkshopResponse(AdminWorkshopResponse.of(p.workshop(), expired), p.ownerLink().path(),
                p.ownerLink().expiresAt());
    }
}

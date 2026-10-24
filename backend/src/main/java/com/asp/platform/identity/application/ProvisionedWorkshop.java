package com.asp.platform.identity.application;

import com.asp.platform.identity.domain.WorkshopOverview;

/// A workshop that was just registered, and the activation link for its owner.
public record ProvisionedWorkshop(WorkshopOverview workshop, ActivationLink ownerLink) {
}

package com.asp.platform.workshop.domain;

/// When in the visit a photo was taken, or what it shows. DOCUMENTS (the vehicle registration, an ID card, a signed
/// paper) is for the owner and the front desk only: mechanics never list, read, upload, change or delete them.
public enum PhotoStage {
    CHECK_IN,
    WORK,
    DELIVERY,
    DOCUMENTS;

    /// True when mechanics have no access to photos of this stage.
    public boolean isRestricted() {
        return this == DOCUMENTS;
    }
}

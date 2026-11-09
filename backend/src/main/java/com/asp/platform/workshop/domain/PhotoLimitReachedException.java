package com.asp.platform.workshop.domain;

import com.asp.platform.shared.ConflictException;

/// The work order already has 30 photos. 409 `photo-limit-reached`.
public class PhotoLimitReachedException extends ConflictException {

    public PhotoLimitReachedException() {
        super("photo-limit-reached", "A work order can have at most " + WorkOrderPhoto.MAX_PER_ORDER + " photos");
    }
}

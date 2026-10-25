package com.asp.platform.booking.domain;

import com.asp.platform.shared.ConflictException;

/// The slot is not offered or already full (also when two requests raced for its last place). Mapped to 409 `slot-unavailable`.
public class SlotUnavailableException extends ConflictException {

    public static final String CODE = "slot-unavailable";

    public SlotUnavailableException() {
        super(CODE, "This time is no longer available; choose another one");
    }
}

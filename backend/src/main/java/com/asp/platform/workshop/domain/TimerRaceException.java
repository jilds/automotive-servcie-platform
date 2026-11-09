package com.asp.platform.workshop.domain;

import com.asp.platform.shared.ConflictException;

/// Two timers of the same mechanic started at the same moment on different orders; nothing was stored. 409
/// `try-again`: the client may simply start again.
public class TimerRaceException extends ConflictException {

    public TimerRaceException() {
        super("try-again", "Another timer started at the same moment; try again");
    }
}

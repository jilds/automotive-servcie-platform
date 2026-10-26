package com.asp.platform.identity.domain;

import com.asp.platform.shared.NotFoundException;

public class LeadNotFoundException extends NotFoundException {

    public static final String CODE = "lead-not-found";

    public LeadNotFoundException() {
        super(CODE, "Demo request not found");
    }
}

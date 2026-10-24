package com.asp.platform.identity.domain;

/// The workshop's subscription is suspended or cancelled, so its users may not sign in. Mapped to 403 with code
/// `workshop-suspended`; only said after the password was checked, so it reveals nothing to a stranger.
public class WorkshopSuspendedException extends RuntimeException {

    public static final String CODE = "workshop-suspended";

    public WorkshopSuspendedException() {
        super("This workshop's subscription is suspended; contact the ASP team");
    }
}

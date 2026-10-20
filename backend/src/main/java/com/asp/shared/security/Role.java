package com.asp.shared.security;

/** What a workshop user may see and do. Mechanics never see prices. */
public enum Role {
    OWNER,
    FRONT_DESK,
    MECHANIC;

    public String authority() {
        return "ROLE_" + name();
    }
}

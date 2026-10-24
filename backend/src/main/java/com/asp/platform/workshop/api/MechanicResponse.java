package com.asp.platform.workshop.api;

import java.util.UUID;

import com.asp.platform.identity.TeamMember;

/// An active mechanic a work order can be given to.
record MechanicResponse(UUID id, String name) {

    static MechanicResponse of(TeamMember member) {
        return new MechanicResponse(member.id(), member.name());
    }
}

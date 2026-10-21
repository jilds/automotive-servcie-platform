package com.asp.platform.identity;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/// Public API of the identity module for other modules: who works in a workshop, where and in which time zone.
public interface WorkshopDirectory {

    /// The workshop's time zone, in which calendar periods such as "this month" are counted.
    ZoneId timezone(UUID tenantId);

    /// The workshop's country as an ISO 3166-1 alpha-2 code, for example "PT".
    String country(UUID tenantId);

    /// Active mechanics of the workshop, by name.
    List<TeamMember> activeMechanics(UUID tenantId);

    /// True when the user belongs to the workshop, is active and is a mechanic, so work may be assigned to them.
    boolean isActiveMechanic(UUID tenantId, UUID userId);
}

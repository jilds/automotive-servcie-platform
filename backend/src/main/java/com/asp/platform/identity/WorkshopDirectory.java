package com.asp.platform.identity;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/// Public API of the identity module for other modules: who works in a workshop, where and in which time zone.
public interface WorkshopDirectory {

    /// The workshop's time zone, in which calendar periods such as "this month" are counted.
    ZoneId timezone(UUID tenantId);

    /// The workshop's country as an ISO 3166-1 alpha-2 code, for example "PT".
    String country(UUID tenantId);

    /// Name, slug, country and time zone for a message to the workshop's customers or users; empty for an unknown
    /// workshop. Reads the tenant table, which is not under row-level security, so it works outside a request.
    Optional<WorkshopProfile> profile(UUID tenantId);

    /// The workshop's hourly labour rate before VAT, in cents, from its settings (prices services charged by the hour).
    long labourRateCents(UUID tenantId);

    /// Active mechanics of the workshop, by name.
    List<TeamMember> activeMechanics(UUID tenantId);

    /// The names of everyone in the workshop (active or not, any role), keyed by user id. For showing who did
    /// something in a history; no email or credentials.
    Map<UUID, String> memberNames(UUID tenantId);

    /// True when the user belongs to the workshop, is active and is a mechanic, so work may be assigned to them.
    boolean isActiveMechanic(UUID tenantId, UUID userId);

    /// The workshop whose public pages live at `/w/{slug}`, when the slug is well-formed, the workshop exists and its
    /// subscription lets it sign in (trial or active). Empty otherwise, so a closed workshop reads like an unknown one.
    /// Cached briefly, misses too; a change to the workshop evicts it on this instance at once.
    Optional<PublicWorkshop> publicWorkshop(String slug);

    /// The ids of every workshop whose subscription lets it sign in, oldest first. For jobs that run per workshop
    /// inside a [com.asp.platform.shared.TenantScope].
    List<UUID> openWorkshops();
}

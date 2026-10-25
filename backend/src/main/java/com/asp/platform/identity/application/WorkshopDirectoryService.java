package com.asp.platform.identity.application;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.asp.platform.identity.PublicWorkshop;
import com.asp.platform.identity.TeamMember;
import com.asp.platform.identity.WorkshopDirectory;
import com.asp.platform.identity.domain.Slug;
import com.asp.platform.identity.domain.Tenant;
import com.asp.platform.identity.domain.TenantNotFoundException;
import com.asp.platform.identity.domain.TenantRepository;
import com.asp.platform.identity.domain.User;
import com.asp.platform.identity.domain.UserRepository;
import com.asp.platform.shared.Role;

/// What other modules may look up about a workshop and its team. Neither the tenant nor the user table has
/// row-level security, so every lookup is filtered by the tenant id the caller passes, which is always the
/// signed-in user's.
@Service
class WorkshopDirectoryService implements WorkshopDirectory {

    private final TenantRepository tenants;
    private final UserRepository users;
    private final PublicWorkshopCache publicWorkshops;

    WorkshopDirectoryService(TenantRepository tenants, UserRepository users, PublicWorkshopCache publicWorkshops) {
        this.tenants = tenants;
        this.users = users;
        this.publicWorkshops = publicWorkshops;
    }

    @Override
    @Transactional(readOnly = true)
    public ZoneId timezone(UUID tenantId) {
        return tenant(tenantId).settings().zone();
    }

    @Override
    @Transactional(readOnly = true)
    public String country(UUID tenantId) {
        return tenant(tenantId).settings().country();
    }

    @Override
    @Transactional(readOnly = true)
    public long labourRateCents(UUID tenantId) {
        return tenant(tenantId).settings().labourRateCents();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeamMember> activeMechanics(UUID tenantId) {
        return users.findByTenant(tenantId).stream()
                .filter(WorkshopDirectoryService::isActiveMechanic)
                .map(WorkshopDirectoryService::member)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> memberNames(UUID tenantId) {
        return users.findByTenant(tenantId).stream().collect(Collectors.toUnmodifiableMap(User::id, User::name));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isActiveMechanic(UUID tenantId, UUID userId) {
        return users.findById(userId)
                .filter(user -> user.tenantId().equals(tenantId))
                .filter(WorkshopDirectoryService::isActiveMechanic)
                .isPresent();
    }

    /// Called on every public page request, so the answer is cached ([PublicWorkshopCache], misses too) and a hit
    /// opens no transaction and takes no connection. A miss reads the tenant once by its unique slug.
    @Override
    public Optional<PublicWorkshop> publicWorkshop(String slug) {
        if (!Slug.isValid(slug)) {
            return Optional.empty();
        }
        return publicWorkshops.get(slug, this::loadPublicWorkshop);
    }

    private Optional<PublicWorkshop> loadPublicWorkshop(String slug) {
        return tenants.findBySlug(new Slug(slug))
                .filter(Tenant::allowsSignIn)
                .map(WorkshopDirectoryService::publicWorkshop);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> openWorkshops() {
        return tenants.idsAllowingSignIn();
    }

    private static PublicWorkshop publicWorkshop(Tenant tenant) {
        return new PublicWorkshop(tenant.id(), tenant.shownName(), tenant.settings().zone(),
                tenant.settings().country(), tenant.addOns().booking(), tenant.addOns().shop());
    }

    private Tenant tenant(UUID tenantId) {
        return tenants.findById(tenantId).orElseThrow(TenantNotFoundException::new);
    }

    private static boolean isActiveMechanic(User user) {
        return user.active() && user.role() == Role.MECHANIC;
    }

    private static TeamMember member(User user) {
        return new TeamMember(user.id(), user.name());
    }
}

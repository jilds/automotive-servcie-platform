package com.asp.platform.identity.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.asp.platform.identity.domain.AddOns;
import com.asp.platform.identity.domain.Slug;
import com.asp.platform.identity.domain.SlugTakenException;
import com.asp.platform.identity.domain.Subscription;
import com.asp.platform.identity.domain.Tenant;
import com.asp.platform.identity.domain.TenantNotFoundException;
import com.asp.platform.identity.domain.TenantRepository;
import com.asp.platform.identity.domain.User;
import com.asp.platform.identity.domain.UserNotFoundException;
import com.asp.platform.identity.domain.UserRepository;
import com.asp.platform.identity.domain.WorkshopOverview;
import com.asp.platform.identity.domain.WorkshopSettings;
import com.asp.platform.shared.Role;

/// The platform admin's console: register workshops, manage their subscription and invite their owner.
/// Touches only the tenant, app_user and activation tables (no row-level security, filtered here by the workshop
/// id the admin chose). Nothing here reads a workshop's customers, vehicles or work orders.
@Service
public class WorkshopAdminService {

    private static final Logger log = LoggerFactory.getLogger(WorkshopAdminService.class);

    private final TenantRepository tenants;
    private final UserRepository users;
    private final IdentityService identity;
    private final ActivationLinks links;
    private final AccessDirectoryService access;
    private final PublicWorkshopCache publicWorkshops;
    private final Clock clock;

    WorkshopAdminService(TenantRepository tenants, UserRepository users, IdentityService identity,
            ActivationLinks links, AccessDirectoryService access, PublicWorkshopCache publicWorkshops, Clock clock) {
        this.publicWorkshops = publicWorkshops;
        this.tenants = tenants;
        this.users = users;
        this.identity = identity;
        this.links = links;
        this.access = access;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<WorkshopOverview> workshops() {
        return tenants.overview();
    }

    @Transactional(readOnly = true)
    public WorkshopOverview workshop(UUID tenantId) {
        return overview(tenant(tenantId));
    }

    /// Registers a workshop and its first owner, who has no password yet: the returned link lets them set one.
    ///
    /// @throws SlugTakenException when the requested slug is in use
    @Transactional
    public ProvisionedWorkshop provision(ProvisionWorkshopCommand command) {
        identity.requireUnusedEmail(command.ownerEmail());
        Slug slug = command.slug() != null ? requireFree(new Slug(command.slug())) : freeSlugFor(command.name());
        Tenant tenant = tenants.save(Tenant.provision(UUID.randomUUID(),
                WorkshopSettings.initial(command.name(), command.nif(), command.country(), command.timezone())
                        .checkNif(),
                command.subscription(), slug, clock.instant()));
        publicWorkshops.evictSlug(slug.value());
        User owner = users.save(User.invite(UUID.randomUUID(), tenant.id(), command.ownerName(), command.ownerEmail(),
                Role.OWNER, clock.instant()));
        ActivationLink link = links.issue(owner);
        log.info("Workshop registered: tenantId={} slug={} plan={} status={}", tenant.id(), slug.value(),
                command.subscription().plan(), command.subscription().status());
        return new ProvisionedWorkshop(new WorkshopOverview(tenant, owner.email(), 1), link);
    }

    /// Changes plan, status or end date. Suspending or cancelling stops the workshop's users at their next request,
    /// even with tokens issued before; reactivating lets those tokens work again.
    ///
    /// @param expectedVersion the workshop version the admin read
    /// @throws com.asp.platform.shared.StaleDataException when someone else saved the workshop since
    @Transactional
    public WorkshopOverview changeSubscription(UUID tenantId, Subscription subscription, long expectedVersion) {
        Tenant tenant = tenant(tenantId);
        tenant.requireVersion(expectedVersion);
        Tenant saved = tenants.save(tenant.withSubscription(subscription));
        access.evictWorkshop(tenantId);
        publicWorkshops.evictWorkshop(tenantId, tenant.slug().value());
        log.info("Subscription changed: tenantId={} plan={} status={}", tenantId, subscription.plan(),
                subscription.status());
        return overview(saved);
    }

    /// Switches the online booking and online shop add-ons on or off, independent of the plan. Their public pages
    /// answer 404 while off.
    ///
    /// @param expectedVersion the workshop version the admin read
    /// @throws com.asp.platform.shared.StaleDataException when someone else saved the workshop since
    @Transactional
    public WorkshopOverview changeAddOns(UUID tenantId, AddOns addOns, long expectedVersion) {
        Tenant tenant = tenant(tenantId);
        tenant.requireVersion(expectedVersion);
        Tenant saved = tenants.save(tenant.withAddOns(addOns));
        access.evictWorkshop(tenantId);
        publicWorkshops.evictWorkshop(tenantId, tenant.slug().value());
        log.info("Add-ons changed: tenantId={} booking={} shop={}", tenantId, addOns.booking(), addOns.shop());
        return overview(saved);
    }

    /// A new activation link for the workshop's first owner, also when they activated before (the link then sets a
    /// new password). Their older unused link stops working.
    @Transactional
    public Invitation reissueOwnerInvitation(UUID tenantId) {
        tenant(tenantId);
        User owner = users.findFirstOwner(tenantId).orElseThrow(UserNotFoundException::new);
        return new Invitation(owner, links.issue(owner));
    }

    private WorkshopOverview overview(Tenant tenant) {
        List<User> team = users.findByTenant(tenant.id());
        String ownerEmail = users.findFirstOwner(tenant.id()).map(User::email).orElse(null);
        return new WorkshopOverview(tenant, ownerEmail, team.size());
    }

    private Slug requireFree(Slug slug) {
        if (tenants.existsBySlug(slug)) {
            throw new SlugTakenException();
        }
        return slug;
    }

    private Slug freeSlugFor(String name) {
        Slug base = Slug.fromName(name);
        Slug candidate = base;
        for (int n = 2; tenants.existsBySlug(candidate); n++) {
            candidate = base.numbered(n);
        }
        return candidate;
    }

    private Tenant tenant(UUID tenantId) {
        return tenants.findById(tenantId).orElseThrow(TenantNotFoundException::new);
    }
}

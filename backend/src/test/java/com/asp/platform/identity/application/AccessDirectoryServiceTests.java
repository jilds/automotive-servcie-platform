package com.asp.platform.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.asp.platform.identity.UserAccess;
import com.asp.platform.identity.domain.User;
import com.asp.platform.identity.domain.Plan;
import com.asp.platform.identity.domain.PlatformAdminRepository;
import com.asp.platform.identity.domain.Slug;
import com.asp.platform.identity.domain.Subscription;
import com.asp.platform.identity.domain.SubscriptionStatus;
import com.asp.platform.identity.domain.Tenant;
import com.asp.platform.identity.domain.TenantRepository;
import com.asp.platform.identity.domain.UserRepository;
import com.asp.platform.identity.domain.WorkshopSettings;
import com.asp.platform.shared.Role;

@ExtendWith(MockitoExtension.class)
class AccessDirectoryServiceTests {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final SubscriptionPolicy POLICY = new SubscriptionPolicy(
            Clock.fixed(Instant.parse("2026-10-26T12:00:00Z"), ZoneOffset.UTC), new SubscriptionProperties(7));

    @Mock
    private UserRepository users;

    @Mock
    private TenantRepository tenants;

    @Mock
    private PlatformAdminRepository admins;

    @Test
    void should_read_the_database_once_when_access_is_asked_again_within_the_ttl() {
        when(users.findById(USER)).thenReturn(Optional.of(user(Role.OWNER, true)));
        AccessDirectoryService directory = new AccessDirectoryService(users, tenants, admins, POLICY);

        assertThat(directory.access(USER)).contains(new UserAccess(TENANT, Role.OWNER, true));
        assertThat(directory.access(USER)).contains(new UserAccess(TENANT, Role.OWNER, true));

        verify(users, times(1)).findById(USER);
    }

    @Test
    void should_read_the_new_access_when_the_entry_is_evicted() {
        when(users.findById(USER)).thenReturn(Optional.of(user(Role.OWNER, true)),
                Optional.of(user(Role.MECHANIC, false)));
        AccessDirectoryService directory = new AccessDirectoryService(users, tenants, admins, POLICY);
        directory.access(USER);

        directory.evict(USER);

        assertThat(directory.access(USER)).contains(new UserAccess(TENANT, Role.MECHANIC, false));
        verify(users, times(2)).findById(USER);
    }

    @Test
    void should_not_remember_unknown_users_when_they_are_not_found() {
        when(users.findById(USER)).thenReturn(Optional.empty(), Optional.of(user(Role.FRONT_DESK, true)));
        AccessDirectoryService directory = new AccessDirectoryService(users, tenants, admins, POLICY);

        assertThat(directory.access(USER)).isEmpty();
        assertThat(directory.access(USER)).contains(new UserAccess(TENANT, Role.FRONT_DESK, true));
    }

    @Test
    void should_cache_the_workshop_status_until_it_is_evicted() {
        when(tenants.findById(TENANT)).thenReturn(Optional.of(tenant(SubscriptionStatus.ACTIVE)),
                Optional.of(tenant(SubscriptionStatus.SUSPENDED)));
        AccessDirectoryService directory = new AccessDirectoryService(users, tenants, admins, POLICY);

        assertThat(directory.workshopOpen(TENANT)).isTrue();
        assertThat(directory.workshopOpen(TENANT)).isTrue();
        verify(tenants, times(1)).findById(TENANT);

        directory.evictWorkshop(TENANT);

        assertThat(directory.workshopOpen(TENANT)).isFalse();
        assertThat(directory.workshopOpen(UUID.randomUUID())).as("unknown workshop").isFalse();
    }

    private static Tenant tenant(SubscriptionStatus status) {
        return Tenant.provision(TENANT, WorkshopSettings.initial("Oficina", null, "PT", "Europe/Lisbon"),
                new Subscription(Plan.PRO, status, null), new Slug("oficina"), Instant.EPOCH);
    }

    private static User user(Role role, boolean active) {
        return new User(USER, TENANT, "Rui", "rui@test.pt", "{noop}x", role, active, Instant.EPOCH);
    }
}

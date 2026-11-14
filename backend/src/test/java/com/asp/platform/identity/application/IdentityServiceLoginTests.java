package com.asp.platform.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.asp.platform.identity.domain.InvalidCredentialsException;
import com.asp.platform.identity.domain.PlatformAdmin;
import com.asp.platform.identity.domain.PlatformAdminRepository;
import com.asp.platform.identity.domain.Tenant;
import com.asp.platform.identity.domain.TenantRepository;
import com.asp.platform.identity.domain.User;
import com.asp.platform.identity.domain.UserRepository;
import com.asp.platform.identity.domain.WorkshopSuspendedException;
import com.asp.platform.shared.Role;

/// Login runs exactly one password check, whether or not the account exists, so the answer time does not reveal
/// which e-mails are registered.
class IdentityServiceLoginTests {

    private static final Instant NOW = Instant.parse("2026-11-14T10:00:00Z");

    private final UserRepository users = mock(UserRepository.class);
    private final PlatformAdminRepository admins = mock(PlatformAdminRepository.class);
    private static final String STAND_IN = "{bcrypt}stand-in";

    private final PasswordEncoder encoder = encoderWithStandIn();
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final TokenIssuer tokenIssuer = mock(TokenIssuer.class);
    private final SubscriptionPolicy subscriptions = mock(SubscriptionPolicy.class);
    private final IdentityService service = new IdentityService(tenants, users, admins, encoder,
            tokenIssuer, mock(AccessDirectoryService.class), mock(ActivationLinks.class),
            Clock.fixed(NOW, java.time.ZoneOffset.UTC), subscriptions);

    @Test
    void should_check_a_password_against_the_stand_in_hash_for_an_unknown_email() {
        when(users.findByEmail("nobody@asp.test")).thenReturn(Optional.empty());
        when(admins.findByEmail("nobody@asp.test")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(" Nobody@asp.test ", "whatever-password"))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(encoder, times(1)).matches("whatever-password", STAND_IN);
    }

    @Test
    void should_check_the_stand_in_hash_for_an_inactive_user_and_for_one_who_has_not_activated() {
        User inactive = User.create(UUID.randomUUID(), UUID.randomUUID(), "Ana", "ana@asp.test", "{bcrypt}x",
                Role.OWNER, NOW).withAccess(Role.OWNER, false);
        User pending = User.invite(UUID.randomUUID(), UUID.randomUUID(), "Rui", "rui@asp.test", Role.MECHANIC, NOW);
        when(users.findByEmail("ana@asp.test")).thenReturn(Optional.of(inactive));
        when(users.findByEmail("rui@asp.test")).thenReturn(Optional.of(pending));
        when(admins.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("ana@asp.test", "pw-one-one-one"))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> service.login("rui@asp.test", "pw-two-two-two"))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(encoder).matches("pw-one-one-one", STAND_IN);
        verify(encoder).matches("pw-two-two-two", STAND_IN);
        verify(encoder, never()).matches(any(), eq("{bcrypt}x"));
    }

    @Test
    void should_check_the_stand_in_hash_for_an_inactive_platform_admin() {
        PlatformAdmin admin = new PlatformAdmin(UUID.randomUUID(), "Admin", "admin@asp.test", "{bcrypt}y", false, NOW);
        when(users.findByEmail("admin@asp.test")).thenReturn(Optional.empty());
        when(admins.findByEmail("admin@asp.test")).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.login("admin@asp.test", "pw-admin-admin"))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(encoder, times(1)).matches(eq("pw-admin-admin"), anyString());
        verify(encoder).matches("pw-admin-admin", STAND_IN);
    }

    @Test
    void should_check_the_real_hash_once_for_a_wrong_password() {
        User user = User.create(UUID.randomUUID(), UUID.randomUUID(), "Ana", "ana@asp.test", "{bcrypt}real",
                Role.OWNER, NOW);
        when(users.findByEmail("ana@asp.test")).thenReturn(Optional.of(user));
        when(encoder.matches("wrong-password", "{bcrypt}real")).thenReturn(false);

        assertThatThrownBy(() -> service.login("ana@asp.test", "wrong-password"))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(encoder, times(1)).matches(anyString(), anyString());
    }

    @Test
    void should_sign_in_a_user_with_the_right_password_after_one_password_check() {
        UUID tenantId = UUID.randomUUID();
        User user = User.create(UUID.randomUUID(), tenantId, "Ana", "ana@asp.test", "{bcrypt}real", Role.OWNER, NOW);
        Tenant tenant = mock(Tenant.class);
        IssuedToken token = new IssuedToken("token", Duration.ofHours(12));
        when(users.findByEmail("ana@asp.test")).thenReturn(Optional.of(user));
        when(encoder.matches("right-password-1", "{bcrypt}real")).thenReturn(true);
        when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(subscriptions.allowsSignIn(tenant)).thenReturn(true);
        when(tokenIssuer.issue(user)).thenReturn(token);

        Session session = service.login(" Ana@asp.test ", "right-password-1");

        assertThat(session.token()).isEqualTo(token);
        assertThat(session.account().email()).isEqualTo("ana@asp.test");
        verify(encoder, times(1)).matches(anyString(), anyString());
    }

    @Test
    void should_sign_in_a_platform_admin_with_the_right_password_after_one_password_check() {
        PlatformAdmin admin = new PlatformAdmin(UUID.randomUUID(), "Admin", "admin@asp.test", "{bcrypt}admin", true,
                NOW);
        IssuedToken token = new IssuedToken("admin-token", Duration.ofHours(12));
        when(users.findByEmail("admin@asp.test")).thenReturn(Optional.empty());
        when(admins.findByEmail("admin@asp.test")).thenReturn(Optional.of(admin));
        when(encoder.matches("right-password-1", "{bcrypt}admin")).thenReturn(true);
        when(tokenIssuer.issue(admin)).thenReturn(token);

        Session session = service.login("admin@asp.test", "right-password-1");

        assertThat(session.token()).isEqualTo(token);
        verify(encoder, times(1)).matches(anyString(), anyString());
    }

    @Test
    void should_refuse_a_suspended_workshop_only_after_the_password_check() {
        UUID tenantId = UUID.randomUUID();
        User user = User.create(UUID.randomUUID(), tenantId, "Ana", "ana@asp.test", "{bcrypt}real", Role.OWNER, NOW);
        Tenant tenant = mock(Tenant.class);
        when(users.findByEmail("ana@asp.test")).thenReturn(Optional.of(user));
        when(encoder.matches("right-password-1", "{bcrypt}real")).thenReturn(true);
        when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(subscriptions.allowsSignIn(tenant)).thenReturn(false);

        assertThatThrownBy(() -> service.login("ana@asp.test", "right-password-1"))
                .isInstanceOf(WorkshopSuspendedException.class);

        verify(encoder, times(1)).matches(anyString(), anyString());
        verify(tokenIssuer, never()).issue(any(User.class));
    }

    @Test
    void should_not_say_a_workshop_is_suspended_when_the_password_is_wrong() {
        UUID tenantId = UUID.randomUUID();
        User user = User.create(UUID.randomUUID(), tenantId, "Ana", "ana@asp.test", "{bcrypt}real", Role.OWNER, NOW);
        when(users.findByEmail("ana@asp.test")).thenReturn(Optional.of(user));
        when(encoder.matches("wrong-password-1", "{bcrypt}real")).thenReturn(false);

        assertThatThrownBy(() -> service.login("ana@asp.test", "wrong-password-1"))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(tenants, never()).findById(any());
    }

    @Test
    void should_make_the_stand_in_hash_with_the_encoder_so_it_has_the_same_cost() {
        verify(encoder).encode(anyString());
    }

    private static PasswordEncoder encoderWithStandIn() {
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode(anyString())).thenReturn(STAND_IN);
        return encoder;
    }
}

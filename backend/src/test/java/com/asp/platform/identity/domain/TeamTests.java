package com.asp.platform.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.asp.platform.shared.Role;

class TeamTests {

    private static final UUID TENANT = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-20T09:00:00Z");

    private final User owner = user("Ana", Role.OWNER);
    private final User mechanic = user("Rui", Role.MECHANIC);
    private final Team team = new Team(List.of(owner, mechanic));

    @Test
    void should_change_role_and_deactivate_a_member() {
        User changed = team.changeAccess(owner.id(), mechanic.id(), Role.FRONT_DESK, false);

        assertThat(changed.role()).isEqualTo(Role.FRONT_DESK);
        assertThat(changed.active()).isFalse();
        assertThat(changed.id()).isEqualTo(mechanic.id());
        assertThat(changed.passwordHash()).isEqualTo(mechanic.passwordHash());
    }

    @Test
    void should_not_let_an_owner_change_their_own_role() {
        assertThatThrownBy(() -> team.changeAccess(owner.id(), owner.id(), Role.FRONT_DESK, true))
                .isInstanceOf(OwnAccountChangeException.class);
    }

    @Test
    void should_not_let_an_owner_deactivate_themselves() {
        assertThatThrownBy(() -> team.changeAccess(owner.id(), owner.id(), Role.OWNER, false))
                .isInstanceOf(OwnAccountChangeException.class);
    }

    @Test
    void should_accept_an_unchanged_own_account() {
        assertThat(team.changeAccess(owner.id(), owner.id(), Role.OWNER, true)).isEqualTo(owner);
    }

    @Test
    void should_let_one_owner_demote_another_while_an_owner_is_left() {
        User second = user("Bea", Role.OWNER);
        Team twoOwners = new Team(List.of(owner, second));

        assertThat(twoOwners.changeAccess(owner.id(), second.id(), Role.FRONT_DESK, true).role())
                .isEqualTo(Role.FRONT_DESK);
    }

    @Test
    void should_always_keep_one_active_owner() {
        User inactiveOwner = user("Ana", Role.OWNER).withAccess(Role.OWNER, false);
        User lastOwner = user("Bea", Role.OWNER);
        Team lastOneStanding = new Team(List.of(inactiveOwner, lastOwner));

        assertThatThrownBy(() -> lastOneStanding.changeAccess(inactiveOwner.id(), lastOwner.id(), Role.OWNER, false))
                .isInstanceOf(LastOwnerException.class);
        assertThatThrownBy(() -> lastOneStanding.changeAccess(inactiveOwner.id(), lastOwner.id(), Role.MECHANIC,
                true)).isInstanceOf(LastOwnerException.class);
    }

    @Test
    void should_not_find_someone_outside_the_team() {
        assertThatThrownBy(() -> team.changeAccess(owner.id(), UUID.randomUUID(), Role.MECHANIC, true))
                .isInstanceOf(UserNotFoundException.class);
    }

    private static User user(String name, Role role) {
        return User.create(UUID.randomUUID(), TENANT, name, name + "@test.pt", "{noop}x", role, NOW);
    }
}

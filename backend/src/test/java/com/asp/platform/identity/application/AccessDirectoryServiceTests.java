package com.asp.platform.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.asp.platform.identity.UserAccess;
import com.asp.platform.identity.domain.User;
import com.asp.platform.identity.domain.UserRepository;
import com.asp.platform.shared.Role;

@ExtendWith(MockitoExtension.class)
class AccessDirectoryServiceTests {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    @Mock
    private UserRepository users;

    @Test
    void should_read_the_database_once_when_access_is_asked_again_within_the_ttl() {
        when(users.findById(USER)).thenReturn(Optional.of(user(Role.OWNER, true)));
        AccessDirectoryService directory = new AccessDirectoryService(users);

        assertThat(directory.access(USER)).contains(new UserAccess(TENANT, Role.OWNER, true));
        assertThat(directory.access(USER)).contains(new UserAccess(TENANT, Role.OWNER, true));

        verify(users, times(1)).findById(USER);
    }

    @Test
    void should_read_the_new_access_when_the_entry_is_evicted() {
        when(users.findById(USER)).thenReturn(Optional.of(user(Role.OWNER, true)),
                Optional.of(user(Role.MECHANIC, false)));
        AccessDirectoryService directory = new AccessDirectoryService(users);
        directory.access(USER);

        directory.evict(USER);

        assertThat(directory.access(USER)).contains(new UserAccess(TENANT, Role.MECHANIC, false));
        verify(users, times(2)).findById(USER);
    }

    @Test
    void should_not_remember_unknown_users_when_they_are_not_found() {
        when(users.findById(USER)).thenReturn(Optional.empty(), Optional.of(user(Role.FRONT_DESK, true)));
        AccessDirectoryService directory = new AccessDirectoryService(users);

        assertThat(directory.access(USER)).isEmpty();
        assertThat(directory.access(USER)).contains(new UserAccess(TENANT, Role.FRONT_DESK, true));
    }

    private static User user(Role role, boolean active) {
        return new User(USER, TENANT, "Rui", "rui@test.pt", "{noop}x", role, active, Instant.EPOCH);
    }
}

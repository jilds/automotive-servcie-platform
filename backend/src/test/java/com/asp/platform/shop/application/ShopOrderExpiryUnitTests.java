package com.asp.platform.shop.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

import com.asp.platform.identity.WorkshopDirectory;
import com.asp.platform.shared.TenantScope;

/// The job's loop: each workshop inside its own scope, one order at a time, and a failure in one workshop or one
/// order does not stop the others.
class ShopOrderExpiryUnitTests {

    private final WorkshopDirectory directory = mock(WorkshopDirectory.class);
    private final ShopOrderService orders = mock(ShopOrderService.class);
    private final Instant now = Instant.parse("2026-10-27T10:00:00Z");
    private final ShopOrderExpiry expiry = new ShopOrderExpiry(directory, orders, Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void should_run_each_workshop_in_its_scope_and_go_on_after_a_failure() {
        UUID broken = UUID.randomUUID();
        UUID fine = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(directory.openWorkshops()).thenReturn(List.of(broken, fine));
        AtomicReference<Optional<UUID>> scopeOfFine = new AtomicReference<>();
        when(orders.dueIds(eq(now), anyInt())).thenAnswer(call -> {
            Optional<UUID> scope = TenantScope.current();
            if (scope.equals(Optional.of(broken))) {
                throw new IllegalStateException("database down");
            }
            scopeOfFine.set(scope);
            return List.of(first, second);
        });
        when(orders.expire(fine, first, now)).thenReturn(true);
        when(orders.expire(fine, second, now)).thenReturn(true);

        expiry.run();

        verify(orders).expire(fine, first, now);
        verify(orders).expire(fine, second, now);
        verify(orders, never()).expire(eq(broken), any(), any());
        assertThat(scopeOfFine.get()).contains(fine);
        assertThat(TenantScope.current()).isEmpty();
        assertThat(expiry.expireDue(now)).isEqualTo(2);
    }

    @Test
    void should_skip_an_order_that_conflicts_and_expire_the_rest() {
        UUID workshop = UUID.randomUUID();
        UUID conflicting = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        when(directory.openWorkshops()).thenReturn(List.of(workshop));
        when(orders.dueIds(eq(now), anyInt())).thenReturn(List.of(conflicting, other));
        when(orders.expire(workshop, conflicting, now)).thenThrow(new OptimisticLockingFailureException("collected"));
        when(orders.expire(workshop, other, now)).thenReturn(true);

        assertThat(expiry.expireDue(now)).isEqualTo(1);
        verify(orders).expire(workshop, other, now);
    }
}

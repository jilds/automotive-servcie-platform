package com.asp.platform.shared;

import java.util.Optional;
import java.util.UUID;

import org.springframework.transaction.support.TransactionSynchronizationManager;

/// The workshop a piece of work runs for when no one is signed in: a public workshop page (bound from its slug by the
/// web wiring, ADR 0004) or, later, a scheduled job that walks the open workshops. The database wiring binds it to
/// each connection as `app.tenant_id`, so row-level security filters exactly as for a signed-in user (ADR 0002).
///
/// A signed-in user's token always wins over this scope: the two are never mixed. The binding is a [ScopedValue],
/// so it ends with the call that opened it, a nested call restores the outer workshop on return, and nothing leaks
/// to the next request served by the same thread.
///
/// Bind it before the transaction starts: a connection is bound when the transaction takes it from the pool, so a
/// scope opened inside a transaction would keep reading and writing the outer workshop's rows. [#callAs] and
/// [#runAs] therefore refuse to run inside an active transaction; a job opens one transaction per workshop, inside
/// the scope.
public final class TenantScope {

    private static final ScopedValue<UUID> TENANT = ScopedValue.newInstance();

    private TenantScope() {
    }

    /// Runs `work` for the workshop and returns its result.
    ///
    /// @param <T> the result type
    /// @param <X> what `work` may throw
    /// @throws IllegalStateException when called inside an active transaction
    public static <T, X extends Throwable> T callAs(UUID tenantId, ScopedValue.CallableOp<? extends T, X> work)
            throws X {
        requireNoTransaction();
        return ScopedValue.where(TENANT, tenantId).call(work);
    }

    /// Runs `work` for the workshop.
    ///
    /// @throws IllegalStateException when called inside an active transaction
    public static void runAs(UUID tenantId, Runnable work) {
        requireNoTransaction();
        ScopedValue.where(TENANT, tenantId).run(work);
    }

    /// The workshop bound by the innermost [#callAs] or [#runAs] on this thread, or empty outside one.
    public static Optional<UUID> current() {
        return TENANT.isBound() ? Optional.of(TENANT.get()) : Optional.empty();
    }

    private static void requireNoTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("TenantScope must be opened outside a transaction: the transaction's "
                    + "connection is already bound to another workshop (or none)");
        }
    }
}

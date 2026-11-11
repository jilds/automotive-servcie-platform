package com.asp.platform.reporting.application;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.asp.platform.reporting.domain.ReportBusyException;

/// Reports are heavy reads that each hold a database connection for a while, so two limits apply, per instance:
/// at most `app.reports.max-concurrent-per-workshop` (2) at a time **per workshop** (one more is refused at once, so
/// one workshop cannot take every slot, and an owner changing filters quickly still gets through), and at most
/// `app.reports.max-concurrent` (3) **in all**; the next one waits up to `app.reports.max-wait`. Either way the answer
/// is [ReportBusyException] (429 `try-again`). Both are taken before the transaction starts, so a waiting request holds
/// no connection; the workshop's permit first, released last.
@Component
public class ReportBulkhead {

    private final Semaphore slots;
    private final ConcurrentHashMap<UUID, Integer> running = new ConcurrentHashMap<>();
    private final ReportProperties properties;

    ReportBulkhead(ReportProperties properties) {
        this.properties = properties;
        this.slots = new Semaphore(properties.maxConcurrent());
    }

    /// Runs `work` for the workshop in a slot.
    ///
    /// @throws ReportBusyException when the workshop already has its share of reports running, or no slot became free
    ///                             in time
    public <T> T run(UUID tenantId, Supplier<T> work) {
        if (!enter(tenantId)) {
            throw new ReportBusyException();
        }
        try {
            if (!acquire()) {
                throw new ReportBusyException();
            }
            try {
                return work.get();
            } finally {
                slots.release();
            }
        } finally {
            running.computeIfPresent(tenantId, (_, count) -> count <= 1 ? null : count - 1);
        }
    }

    /// Counts one more running report of the workshop, unless it is at its limit.
    private boolean enter(UUID tenantId) {
        boolean[] admitted = {false};
        running.compute(tenantId, (_, count) -> {
            int current = count == null ? 0 : count;
            admitted[0] = current < properties.maxConcurrentPerWorkshop();
            return admitted[0] ? current + 1 : count;
        });
        return admitted[0];
    }

    private boolean acquire() {
        try {
            return slots.tryAcquire(properties.maxWait().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}

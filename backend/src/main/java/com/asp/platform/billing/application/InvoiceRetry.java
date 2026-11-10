package com.asp.platform.billing.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.asp.platform.identity.WorkshopDirectory;
import com.asp.platform.shared.TenantScope;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/// Every 5 minutes, asks the provider again about the PENDING documents whose outcome is unknown: for each open
/// workshop, inside its [TenantScope] (opened before any transaction), at least `app.billing.retry.min-age` after the
/// last attempt and at most `app.billing.retry.max-attempts` times in all; after that only staff retry. A failure is
/// logged with the workshop id only and does not stop the others. ShedLock makes one instance run it at a time;
/// scheduling is off where `app.scheduling.enabled` is false (the tests call [#retryDue] directly).
@Component
public class InvoiceRetry {

    private static final Logger log = LoggerFactory.getLogger(InvoiceRetry.class);
    private static final int BATCH = 100;

    private final WorkshopDirectory directory;
    private final InvoiceService service;
    private final InvoiceIssuing issuing;
    private final BillingProperties properties;
    private final Clock clock;

    InvoiceRetry(WorkshopDirectory directory, InvoiceService service, InvoiceIssuing issuing,
            BillingProperties properties, Clock clock) {
        this.directory = directory;
        this.service = service;
        this.issuing = issuing;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.billing.retry-cron:0 */5 * * * *}", zone = "UTC")
    @SchedulerLock(name = "invoice-retry", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    public void run() {
        retryDue(clock.instant());
    }

    /// @return how many documents were asked again, over all workshops
    public int retryDue(Instant now) {
        int retried = 0;
        for (UUID tenant : directory.openWorkshops()) {
            try {
                retried += TenantScope.callAs(tenant, () -> retryWorkshop(tenant, now));
            } catch (RuntimeException e) {
                log.warn("Invoice retry failed tenant={} error={}", tenant, e.getClass().getSimpleName());
            }
        }
        if (retried > 0) {
            log.info("Pending invoices retried count={}", retried);
        }
        return retried;
    }

    /// The due documents of the current workshop; one that fails is skipped for this run.
    private int retryWorkshop(UUID tenant, Instant now) {
        int retried = 0;
        List<UUID> due = service.retryable(now.minus(properties.retry().minAge()), properties.retry().maxAttempts(), BATCH);
        for (UUID id : due) {
            try {
                issuing.retry(tenant, id);
                retried++;
            } catch (RuntimeException e) {
                log.warn("Invoice retry skipped a document tenant={} error={}", tenant, e.getClass().getSimpleName());
            }
        }
        return retried;
    }
}

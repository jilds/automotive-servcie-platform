package com.asp.platform.billing.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.asp.platform.billing.domain.PaymentRepository;
import com.asp.platform.identity.WorkshopDirectory;
import com.asp.platform.shared.TenantScope;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/// Every 10 minutes, deals with the pending Multibanco references and MB WAY requests past their validity: it asks the
/// provider first (a lost webhook may hide a payment that was made) and marks the rest EXPIRED. For each open workshop,
/// inside its [TenantScope] (closed workshops included), in keyset batches with one transaction per payment. A failure
/// is logged with the workshop id only.
/// ShedLock makes one instance run it at a time; scheduling is off where `app.scheduling.enabled` is false (the tests
/// call [#expireDue] directly).
@Component
public class PaymentExpiry {

    private static final Logger log = LoggerFactory.getLogger(PaymentExpiry.class);
    static final int BATCH = 200;

    private final WorkshopDirectory directory;
    private final PaymentService payments;
    private final Clock clock;

    PaymentExpiry(WorkshopDirectory directory, PaymentService payments, Clock clock) {
        this.directory = directory;
        this.payments = payments;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.billing.expiry-cron:0 */10 * * * *}", zone = "UTC")
    @SchedulerLock(name = "payment-expiry", lockAtMostFor = "PT20M", lockAtLeastFor = "PT1M")
    public void run() {
        expireDue(clock.instant());
    }

    /// @return how many payments changed, over all workshops
    public int expireDue(Instant now) {
        int changed = 0;
        // Every workshop, closed ones too: a suspended workshop gets no webhooks (the public pages answer 404), so this
        // poll of the provider is how its payments are settled.
        for (UUID tenant : directory.allWorkshops()) {
            try {
                changed += TenantScope.callAs(tenant, () -> expireWorkshop(tenant, now));
            } catch (RuntimeException e) {
                log.warn("Payment expiry failed tenant={} error={}", tenant, e.getClass().getSimpleName());
            }
        }
        if (changed > 0) {
            log.info("Pending payments settled count={}", changed);
        }
        return changed;
    }

    /// Keyset paging on (expires_at, id): a payment that stays pending (the provider cannot be asked) is passed once,
    /// never read again in this run.
    private int expireWorkshop(UUID tenant, Instant now) {
        int changed = 0;
        PaymentRepository.Cursor after = null;
        while (true) {
            List<PaymentRepository.Cursor> due = payments.dueForExpiry(now, after, BATCH);
            for (PaymentRepository.Cursor cursor : due) {
                try {
                    if (payments.expireOne(tenant, cursor.id(), now)) {
                        changed++;
                    }
                } catch (RuntimeException e) {
                    log.warn("Payment expiry skipped a payment tenant={} error={}", tenant,
                            e.getClass().getSimpleName());
                }
            }
            if (due.size() < BATCH) {
                return changed;
            }
            after = due.getLast();
        }
    }
}

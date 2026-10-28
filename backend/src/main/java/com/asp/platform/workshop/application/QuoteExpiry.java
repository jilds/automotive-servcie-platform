package com.asp.platform.workshop.application;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.asp.platform.identity.WorkshopDirectory;
import com.asp.platform.shared.TenantScope;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/// Every hour, stores the SENT quotes past their validity as EXPIRED (they already read EXPIRED everywhere): for each
/// open workshop, inside its [TenantScope] (opened before any transaction), in batches of [#BATCH] with one
/// transaction per quote ([QuoteService#expire]). The work orders do not move. A failure is logged with the workshop
/// id only and does not stop the others. ShedLock makes one instance run it at a time; scheduling is off where
/// `app.scheduling.enabled` is false (the tests call [#expireDue] directly).
@Component
public class QuoteExpiry {

    private static final Logger log = LoggerFactory.getLogger(QuoteExpiry.class);

    /// Due quotes read per query.
    static final int BATCH = 200;

    private final WorkshopDirectory directory;
    private final QuoteService quotes;
    private final Clock clock;

    QuoteExpiry(WorkshopDirectory directory, QuoteService quotes, Clock clock) {
        this.directory = directory;
        this.quotes = quotes;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.quotes.expiry-cron:0 5 * * * *}", zone = "UTC")
    @SchedulerLock(name = "quote-expiry", lockAtMostFor = "PT50M", lockAtLeastFor = "PT1M")
    public void run() {
        expireDue(clock.instant());
    }

    /// @return how many quotes expired, over all workshops
    public int expireDue(Instant now) {
        int expired = 0;
        for (UUID tenant : directory.openWorkshops()) {
            try {
                expired += TenantScope.callAs(tenant, () -> expireWorkshop(tenant, now));
            } catch (RuntimeException e) {
                log.warn("Quote expiry failed tenant={} error={}", tenant, e.getClass().getSimpleName());
            }
        }
        if (expired > 0) {
            log.info("Quotes expired count={}", expired);
        }
        return expired;
    }

    /// The due quotes of the current workshop in batches, each in its own transaction. A quote that fails (for
    /// example its work order is locked by a long request) is skipped and tried again next hour.
    private int expireWorkshop(UUID tenant, Instant now) {
        int expired = 0;
        Set<UUID> skipped = new HashSet<>();
        while (true) {
            List<UUID> due = quotes.dueIds(now, BATCH);
            List<UUID> fresh = due.stream().filter(id -> !skipped.contains(id)).toList();
            for (UUID id : fresh) {
                try {
                    if (quotes.expire(id, now)) {
                        expired++;
                    } else {
                        skipped.add(id);
                    }
                } catch (RuntimeException e) {
                    skipped.add(id);
                    log.warn("Quote expiry skipped a quote tenant={} error={}", tenant, e.getClass().getSimpleName());
                }
            }
            if (fresh.isEmpty() || due.size() < BATCH) {
                return expired;
            }
        }
    }
}

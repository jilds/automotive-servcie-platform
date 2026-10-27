package com.asp.platform.shop.application;

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

/// Every hour, expires the open shop orders whose hold time has passed and releases their stock: for each open
/// workshop, inside its [TenantScope] (opened before any transaction), in batches of [#BATCH] with one transaction
/// per order ([ShopOrderService#expire]). A failure is logged with the workshop id only and does not stop the
/// others. ShedLock makes one instance run it at a time; scheduling is off where `app.scheduling.enabled` is false
/// (the tests call [#expireDue] directly).
@Component
public class ShopOrderExpiry {

    private static final Logger log = LoggerFactory.getLogger(ShopOrderExpiry.class);

    /// Due orders read per query.
    static final int BATCH = 200;

    private final WorkshopDirectory directory;
    private final ShopOrderService orders;
    private final Clock clock;

    ShopOrderExpiry(WorkshopDirectory directory, ShopOrderService orders, Clock clock) {
        this.directory = directory;
        this.orders = orders;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.shop.expiry-cron:0 0 * * * *}", zone = "UTC")
    @SchedulerLock(name = "shop-order-expiry", lockAtMostFor = "PT50M", lockAtLeastFor = "PT1M")
    public void run() {
        expireDue(clock.instant());
    }

    /// @return how many orders expired, over all workshops
    public int expireDue(Instant now) {
        int expired = 0;
        for (UUID tenant : directory.openWorkshops()) {
            try {
                expired += TenantScope.callAs(tenant, () -> expireWorkshop(tenant, now));
            } catch (RuntimeException e) {
                log.warn("Shop order expiry failed tenant={} error={}", tenant, e.getClass().getSimpleName());
            }
        }
        if (expired > 0) {
            log.info("Shop orders expired count={}", expired);
        }
        return expired;
    }

    /// The due orders of the current workshop in batches, each order in its own transaction. An order that fails
    /// (for example a version conflict with the front desk) is skipped and tried again next hour.
    private int expireWorkshop(UUID tenant, Instant now) {
        int expired = 0;
        Set<UUID> skipped = new HashSet<>();
        while (true) {
            List<UUID> due = orders.dueIds(now, BATCH);
            List<UUID> fresh = due.stream().filter(id -> !skipped.contains(id)).toList();
            for (UUID id : fresh) {
                try {
                    if (orders.expire(tenant, id, now)) {
                        expired++;
                    } else {
                        skipped.add(id);
                    }
                } catch (RuntimeException e) {
                    skipped.add(id);
                    log.warn("Shop order expiry skipped an order tenant={} error={}", tenant,
                            e.getClass().getSimpleName());
                }
            }
            if (fresh.isEmpty() || due.size() < BATCH) {
                return expired;
            }
        }
    }
}

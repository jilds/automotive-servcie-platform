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

/// Every night, drops the bytes of photos deleted more than `app.photos.deleted-retention-days` ago: for every
/// workshop (suspended, expired and cancelled ones too: [WorkshopDirectory#allWorkshops]), inside its [TenantScope] (opened before any transaction), in batches of [#BATCH] with one transaction
/// per photo ([PhotoService#purge]). The photo's record stays as the audit. A failure is logged with the workshop id
/// only and does not stop the others. ShedLock makes one instance run it at a time; scheduling is off where
/// `app.scheduling.enabled` is false (the tests call [#purgeDue] directly).
@Component
public class PhotoPurge {

    private static final Logger log = LoggerFactory.getLogger(PhotoPurge.class);

    /// Due photos read per query.
    static final int BATCH = 200;

    private final WorkshopDirectory directory;
    private final PhotoService photos;
    private final Clock clock;

    PhotoPurge(WorkshopDirectory directory, PhotoService photos, Clock clock) {
        this.directory = directory;
        this.photos = photos;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.photos.purge-cron:0 30 3 * * *}", zone = "UTC")
    @SchedulerLock(name = "photo-purge", lockAtMostFor = "PT50M", lockAtLeastFor = "PT1M")
    public void run() {
        purgeDue(clock.instant());
    }

    /// @return how many photos lost their bytes, over all workshops
    public int purgeDue(Instant now) {
        int purged = 0;
        for (UUID tenant : directory.allWorkshops()) {
            try {
                purged += TenantScope.callAs(tenant, () -> purgeWorkshop(tenant, now));
            } catch (RuntimeException e) {
                log.warn("Photo purge failed tenant={} error={}", tenant, e.getClass().getSimpleName());
            }
        }
        if (purged > 0) {
            log.info("Photo bytes purged count={}", purged);
        }
        return purged;
    }

    private int purgeWorkshop(UUID tenant, Instant now) {
        int purged = 0;
        Set<UUID> skipped = new HashSet<>();
        while (true) {
            List<UUID> due = photos.purgeDueIds(now, BATCH);
            List<UUID> fresh = due.stream().filter(id -> !skipped.contains(id)).toList();
            for (UUID id : fresh) {
                try {
                    if (photos.purge(id, now)) {
                        purged++;
                    } else {
                        skipped.add(id);
                    }
                } catch (RuntimeException e) {
                    skipped.add(id);
                    log.warn("Photo purge skipped a photo tenant={} error={}", tenant, e.getClass().getSimpleName());
                }
            }
            if (fresh.isEmpty() || due.size() < BATCH) {
                return purged;
            }
        }
    }
}

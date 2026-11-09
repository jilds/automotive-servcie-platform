package com.asp.platform.workshop.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/// Row-level security limits every query to the current workshop.
interface SpringDataPhotoRepository extends JpaRepository<PhotoEntity, UUID> {

    List<PhotoEntity> findByWorkOrderIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(UUID workOrderId);

    int countByWorkOrderIdAndDeletedAtIsNull(UUID workOrderId);

    /// Uses the partial index `work_order_photo_stored_idx` (V19).
    @Query("select coalesce(sum(p.sizeBytes), 0) from PhotoEntity p where p.purgedAt is null")
    long storedBytes();

    @Query(nativeQuery = true, value = """
            select id from work_order_photo
            where purged_at is null and deleted_at is not null and deleted_at < :cutoff
            order by deleted_at, id
            limit :limit""")
    List<UUID> findPurgeDueIds(Instant cutoff, int limit);
}

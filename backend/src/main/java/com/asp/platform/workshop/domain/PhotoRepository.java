package com.asp.platform.workshop.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/// The metadata of the photos; row-level security limits every query to the current workshop. Deleted photos stay in
/// the table (soft delete); the listing and the count leave them out.
public interface PhotoRepository {

    WorkOrderPhoto save(WorkOrderPhoto photo);

    /// Also a deleted photo: the caller checks [WorkOrderPhoto#isDeleted].
    Optional<WorkOrderPhoto> findById(UUID id);

    /// Not deleted, oldest first.
    List<WorkOrderPhoto> findByWorkOrder(UUID workOrderId);

    /// Not deleted.
    int countByWorkOrder(UUID workOrderId);

    /// Takes the workshop's storage-quota lock until the transaction ends (a transaction-scoped advisory lock), so
    /// two uploads of one workshop on different work orders cannot both pass the quota check. Call it before
    /// [#storedBytes].
    void lockStorageQuota(java.util.UUID tenantId);

    /// Bytes still stored for the workshop: the size of every photo whose bytes are not purged yet (deleted ones
    /// included until the purge).
    long storedBytes();

    /// Ids of deleted photos deleted before `cutoff` whose bytes are still stored, oldest first.
    List<UUID> findPurgeDueIds(Instant cutoff, int limit);
}

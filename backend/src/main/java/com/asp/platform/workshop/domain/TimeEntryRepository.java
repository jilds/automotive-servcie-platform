package com.asp.platform.workshop.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/// Row-level security limits every query to the current workshop.
public interface TimeEntryRepository {

    /// Inserts or updates. A second running entry of the same mechanic (a race between two starts on different
    /// orders) breaks the unique index and answers 409 `try-again`.
    TimeEntry save(TimeEntry entry);

    Optional<TimeEntry> findById(UUID id);

    /// Newest start first.
    List<TimeEntry> findByWorkOrder(UUID workOrderId);

    /// The mechanic's running entry on any work order of the workshop.
    Optional<TimeEntry> findRunningByMechanic(UUID mechanicId);

    /// Running entries of the order (at most one in practice).
    List<TimeEntry> findRunningByWorkOrder(UUID workOrderId);

    /// Takes the mechanic's time lock until the transaction ends (a transaction-scoped advisory lock), so requests that
    /// start, stop, correct or add time for one mechanic on different work orders run one after the other. Call it
    /// before reading the mechanic's entries; [#save] takes it too (the same transaction may take it again).
    void lockMechanic(UUID mechanicId);
}

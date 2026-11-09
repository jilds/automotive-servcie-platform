package com.asp.platform.workshop.domain;

import java.util.List;
import java.util.UUID;

/// Row-level security limits every query to the current workshop. Rows are only added.
public interface TimeEntryChangeRepository {

    TimeEntryChange save(TimeEntryChange change);

    /// Oldest first.
    List<TimeEntryChange> findByEntry(UUID entryId);
}

package com.asp.platform.workshop.domain;

import java.util.List;
import java.util.UUID;

/// Row-level security limits every query to the current workshop. Changes are only ever added.
public interface MechanicChangeRepository {

    MechanicChange save(MechanicChange change);

    /// Oldest first.
    List<MechanicChange> findByWorkOrder(UUID workOrderId);
}

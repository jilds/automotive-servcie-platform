package com.asp.platform.workshop.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/// Row-level security limits every query to the current workshop.
public interface WorkOrderChecklistRepository {

    /// Stores a checklist that was just started, with its items.
    WorkOrderChecklist saveNew(WorkOrderChecklist checklist);

    /// Stores the answer of one item.
    void saveAnswer(ChecklistItem item);

    Optional<WorkOrderChecklist> find(UUID workOrderId, ChecklistKind kind);

    /// CHECK_IN first.
    List<WorkOrderChecklist> findByWorkOrder(UUID workOrderId);
}

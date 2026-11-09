package com.asp.platform.workshop.application;

import java.util.Map;
import java.util.UUID;

import com.asp.platform.workshop.domain.WorkOrderChecklist;

/// A started checklist with the names of who started it and who answered its items.
public record ChecklistView(WorkOrderChecklist checklist, Map<UUID, String> names) {
}

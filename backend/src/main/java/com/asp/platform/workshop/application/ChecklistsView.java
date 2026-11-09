package com.asp.platform.workshop.application;

import java.util.List;

/// The started checklists of a work order (CHECK_IN first) and whether the workshop requires the delivery checklist.
public record ChecklistsView(boolean deliveryChecklistRequired, List<ChecklistView> checklists) {
}

package com.asp.platform.workshop.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.asp.platform.workshop.domain.ChecklistResult;
import com.asp.platform.workshop.domain.WorkOrderChecklist;

/// `PUT /work-orders/{id}/checklists/{kind}/items/{itemId}`: OK, ATTENTION or N_A, with an optional note (a blank note
/// removes it).
record ChecklistAnswerRequest(
        @NotNull ChecklistResult result,
        @Nullable @Size(max = WorkOrderChecklist.MAX_NOTE) String note) {
}

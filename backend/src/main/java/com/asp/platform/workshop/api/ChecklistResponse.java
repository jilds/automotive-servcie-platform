package com.asp.platform.workshop.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.asp.platform.workshop.application.ChecklistView;
import com.asp.platform.workshop.domain.ChecklistItem;
import com.asp.platform.workshop.domain.ChecklistKind;
import com.asp.platform.workshop.domain.ChecklistResult;
import com.asp.platform.workshop.domain.WorkOrderChecklist;

/// A started checklist. `complete` is true when every required item has a result.
record ChecklistResponse(ChecklistKind kind, Instant startedAt, @Nullable String startedByName, int requiredTotal,
        int requiredAnswered, boolean complete, List<Item> items) {

    record Item(UUID id, int position, String label, boolean required, @Nullable ChecklistResult result,
            @Nullable String note, @Nullable String answeredByName, @Nullable Instant answeredAt) {
    }

    static ChecklistResponse of(ChecklistView view) {
        WorkOrderChecklist c = view.checklist();
        Map<UUID, String> names = view.names();
        return new ChecklistResponse(c.kind(), c.startedAt(), names.get(c.startedBy()), c.requiredTotal(),
                c.requiredAnswered(), c.isComplete(), c.items().stream().map(i -> item(i, names)).toList());
    }

    private static Item item(ChecklistItem i, Map<UUID, String> names) {
        UUID by = i.answeredBy();
        return new Item(i.id(), i.position(), i.label(), i.required(), i.result(), i.note(),
                by == null ? null : names.get(by), i.answeredAt());
    }
}

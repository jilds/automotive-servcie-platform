package com.asp.platform.workshop.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

/// The checklist of one kind of a work order: a copy of the workshop's template taken when it was started, with an
/// answer per item.
public final class WorkOrderChecklist {

    /// The longest note kept with an answer.
    public static final int MAX_NOTE = 300;

    private final UUID id;
    private final UUID workOrderId;
    private final ChecklistKind kind;
    private final Instant startedAt;
    private final UUID startedBy;
    private final List<ChecklistItem> items;

    public WorkOrderChecklist(UUID id, UUID workOrderId, ChecklistKind kind, Instant startedAt, UUID startedBy,
            List<ChecklistItem> items) {
        this.id = id;
        this.workOrderId = workOrderId;
        this.kind = kind;
        this.startedAt = startedAt;
        this.startedBy = startedBy;
        this.items = new ArrayList<>(items);
    }

    /// Copies the template: every item starts open.
    public static WorkOrderChecklist start(UUID id, UUID workOrderId, ChecklistTemplate template, UUID startedBy,
            Instant now, Supplier<UUID> itemIds) {
        List<ChecklistItem> copied = new ArrayList<>();
        int position = 1;
        for (TemplateItem item : template.items()) {
            copied.add(new ChecklistItem(itemIds.get(), position++, item.label(), item.required(), null, null, null,
                    null));
        }
        return new WorkOrderChecklist(id, workOrderId, template.kind(), now, startedBy, copied);
    }

    /// Gives or changes the answer of one item and returns it.
    ///
    /// @throws ChecklistItemNotFoundException when the item is not in this checklist
    /// @throws InvalidChecklistNoteException  when the note is over [#MAX_NOTE] characters
    public ChecklistItem answer(UUID itemId, ChecklistResult result, @Nullable String note, UUID by, Instant now) {
        String text = note == null || note.isBlank() ? null : note.strip();
        if (text != null && text.length() > MAX_NOTE) {
            throw new InvalidChecklistNoteException();
        }
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id().equals(itemId)) {
                ChecklistItem answered = items.get(i).answer(result, text, by, now);
                items.set(i, answered);
                return answered;
            }
        }
        throw new ChecklistItemNotFoundException();
    }

    public int requiredTotal() {
        return (int) items.stream().filter(ChecklistItem::required).count();
    }

    public int requiredAnswered() {
        return (int) items.stream().filter(item -> item.required() && item.answered()).count();
    }

    /// Every required item has a result.
    public boolean isComplete() {
        return requiredAnswered() == requiredTotal();
    }

    public UUID id() {
        return id;
    }

    public UUID workOrderId() {
        return workOrderId;
    }

    public ChecklistKind kind() {
        return kind;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public UUID startedBy() {
        return startedBy;
    }

    public List<ChecklistItem> items() {
        return List.copyOf(items);
    }
}

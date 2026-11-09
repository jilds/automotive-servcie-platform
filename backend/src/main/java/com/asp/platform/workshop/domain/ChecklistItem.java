package com.asp.platform.workshop.domain;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/// One item of a started checklist: the template item as copied (label and `required` are frozen) and its answer.
///
/// @param result     null while open
/// @param answeredBy who gave the latest answer; null while open
public record ChecklistItem(UUID id, int position, String label, boolean required, @Nullable ChecklistResult result,
        @Nullable String note, @Nullable UUID answeredBy, @Nullable Instant answeredAt) {

    public boolean answered() {
        return result != null;
    }

    ChecklistItem answer(ChecklistResult newResult, @Nullable String newNote, UUID by, Instant now) {
        return new ChecklistItem(id, position, label, required, newResult, newNote, by, now);
    }
}

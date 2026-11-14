package com.asp.platform.customer.api;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.asp.platform.customer.application.NoteService;

record NoteResponse(UUID id, String body, UUID authorId, @Nullable String authorName, Instant createdAt) {

    static NoteResponse of(NoteService.Entry entry) {
        return new NoteResponse(entry.note().id(), entry.note().body(), entry.note().authorId(), entry.authorName(),
                entry.note().createdAt());
    }
}

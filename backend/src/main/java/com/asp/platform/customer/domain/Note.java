package com.asp.platform.customer.domain;

import java.time.Instant;
import java.util.UUID;

/// A dated observation written by a user: on a vehicle (`subjectId` is the vehicle) or on a customer (the customer).
/// Added once, never edited or deleted.
public record Note(UUID id, UUID subjectId, String body, UUID authorId, Instant createdAt) {

    public static final int MAX_LENGTH = 2000;

    public Note {
        body = body.strip();
        if (body.isEmpty() || body.length() > MAX_LENGTH) {
            throw new InvalidNoteException();
        }
    }
}

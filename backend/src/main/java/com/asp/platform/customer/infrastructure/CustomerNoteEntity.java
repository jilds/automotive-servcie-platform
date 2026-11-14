package com.asp.platform.customer.infrastructure;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.asp.platform.customer.domain.Note;

/// `tenant_id` is filled by the database from the connection's workshop and checked by row-level security.
@Entity
@Table(name = "customer_note")
class CustomerNoteEntity {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(nullable = false, updatable = false)
    private String body;

    @Column(name = "author_id", nullable = false, updatable = false)
    private UUID authorId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @SuppressWarnings("NullAway.Init")
    protected CustomerNoteEntity() {
    }

    CustomerNoteEntity(Note note) {
        this.id = note.id();
        this.customerId = note.subjectId();
        this.body = note.body();
        this.authorId = note.authorId();
        this.createdAt = note.createdAt();
    }

    Note toDomain() {
        return new Note(id, customerId, body, authorId, createdAt);
    }
}

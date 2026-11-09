package com.asp.platform.workshop.infrastructure;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.UpdateTimestamp;
import org.jspecify.annotations.Nullable;

import com.asp.platform.shared.persistence.DomainEntity;
import com.asp.platform.workshop.domain.ImageType;
import com.asp.platform.workshop.domain.PhotoStage;
import com.asp.platform.workshop.domain.WorkOrderPhoto;

/// The metadata of a photo; the bytes are in `work_order_photo_data` (see `DbPhotoStore`). `tenant_id` is filled by
/// the database from the connection's workshop and checked by row-level security. Only the stage and the caption
/// change after the upload.
@Entity
@Table(name = "work_order_photo")
class PhotoEntity implements DomainEntity<WorkOrderPhoto> {

    @Id
    private UUID id;

    @Column(name = "work_order_id", nullable = false, updatable = false)
    private UUID workOrderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PhotoStage stage;

    private @Nullable String caption;

    @Column(name = "content_type", nullable = false, updatable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private int sizeBytes;

    @Column(nullable = false, updatable = false)
    private String sha256;

    @Column(name = "uploaded_by", nullable = false, updatable = false)
    private UUID uploadedBy;

    @Column(name = "deleted_at")
    private @Nullable Instant deletedAt;

    @Column(name = "deleted_by")
    private @Nullable UUID deletedBy;

    @Column(name = "delete_reason")
    private @Nullable String deleteReason;

    @Column(name = "purged_at")
    private @Nullable Instant purgedAt;

    /// Optimistic lock; null until first saved, so Spring Data persists new entities instead of merging them.
    @Version
    private @Nullable Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private @Nullable Instant updatedAt;

    @SuppressWarnings("NullAway.Init")
    protected PhotoEntity() {
    }

    PhotoEntity(WorkOrderPhoto photo) {
        this.id = photo.id();
        this.workOrderId = photo.workOrderId();
        this.contentType = photo.type().contentType();
        this.sizeBytes = photo.sizeBytes();
        this.sha256 = photo.sha256();
        this.uploadedBy = photo.uploadedBy();
        this.createdAt = photo.createdAt();
        apply(photo);
    }

    @Override
    public void apply(WorkOrderPhoto photo) {
        this.stage = photo.stage();
        this.caption = photo.caption();
        this.deletedAt = photo.deletedAt();
        this.deletedBy = photo.deletedBy();
        this.deleteReason = photo.deleteReason();
        this.purgedAt = photo.purgedAt();
    }

    @Override
    public WorkOrderPhoto toDomain() {
        Long v = version;
        return new WorkOrderPhoto(id, workOrderId, stage, caption, ImageType.ofContentType(contentType), sizeBytes,
                sha256, uploadedBy, createdAt, deletedAt, deletedBy, deleteReason, purgedAt,
                v == null ? 0 : v);
    }
}

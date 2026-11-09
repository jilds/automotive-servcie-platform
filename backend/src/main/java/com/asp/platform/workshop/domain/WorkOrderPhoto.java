package com.asp.platform.workshop.domain;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.asp.platform.shared.StaleDataException;

/// The metadata of one photo of a work order; the bytes live behind [PhotoStore]. Only the stage and the caption
/// change after the upload. A delete is a soft delete (who, when, why): the photo disappears for everyone, its bytes
/// are dropped later (`purgedAt`) and this record stays as the audit.
public final class WorkOrderPhoto {

    /// 5 MB.
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    public static final int MAX_PER_ORDER = 30;
    public static final int MAX_CAPTION = 200;
    public static final int MAX_REASON = 500;

    private final UUID id;
    private final UUID workOrderId;
    private PhotoStage stage;
    private @Nullable String caption;
    private final ImageType type;
    private final int sizeBytes;
    private final String sha256;
    private final UUID uploadedBy;
    private final Instant createdAt;
    private @Nullable Instant deletedAt;
    private @Nullable UUID deletedBy;
    private @Nullable String deleteReason;
    private @Nullable Instant purgedAt;
    private final long version;

    public WorkOrderPhoto(UUID id, UUID workOrderId, PhotoStage stage, @Nullable String caption, ImageType type,
            int sizeBytes, String sha256, UUID uploadedBy, Instant createdAt, @Nullable Instant deletedAt,
            @Nullable UUID deletedBy, @Nullable String deleteReason, @Nullable Instant purgedAt, long version) {
        this.id = id;
        this.workOrderId = workOrderId;
        this.stage = stage;
        this.caption = clean(caption);
        this.type = type;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.uploadedBy = uploadedBy;
        this.createdAt = createdAt;
        this.deletedAt = deletedAt;
        this.deletedBy = deletedBy;
        this.deleteReason = deleteReason;
        this.purgedAt = purgedAt;
        this.version = version;
    }

    /// A photo ready to store: its metadata and the bytes to keep (the upload without EXIF and XMP metadata).
    public record Accepted(WorkOrderPhoto photo, byte[] bytes) {
    }

    /// Checks the bytes of an upload (size, then the format by the first bytes), removes the location and camera
    /// metadata and describes the photo. The size and the SHA-256 are those of the stored bytes.
    ///
    /// @throws PhotoTooLargeException       over [#MAX_BYTES]
    /// @throws PhotoTypeNotAllowedException when the bytes are not a JPEG, PNG or WebP image
    /// @throws InvalidPhotoInputException   for an empty file or a caption over [#MAX_CAPTION] characters
    public static Accepted accept(UUID id, UUID workOrderId, PhotoStage stage, @Nullable String caption,
            byte[] bytes, UUID uploadedBy, Instant now) {
        if (bytes.length == 0) {
            throw new InvalidPhotoInputException("file", "The photo is empty");
        }
        if (bytes.length > MAX_BYTES) {
            throw new PhotoTooLargeException();
        }
        ImageType type = ImageType.sniff(bytes).orElseThrow(PhotoTypeNotAllowedException::new);
        byte[] clean = ImageSanitizer.strip(type, bytes);
        return new Accepted(new WorkOrderPhoto(id, workOrderId, stage, caption, type, clean.length, sha256(clean),
                uploadedBy, now, null, null, null, null, 0), clean);
    }

    /// @throws InvalidPhotoInputException for a caption over [#MAX_CAPTION] characters
    public void describe(PhotoStage newStage, @Nullable String newCaption) {
        this.stage = newStage;
        this.caption = clean(newCaption);
    }

    /// Soft delete with the reason.
    ///
    /// @throws ReasonRequiredException without a reason (or over [#MAX_REASON] characters)
    public void markDeleted(UUID by, @Nullable String reason, Instant now) {
        String why = reason == null ? "" : reason.strip();
        if (why.isEmpty()) {
            throw new ReasonRequiredException("Say why the photo is deleted");
        }
        if (why.length() > MAX_REASON) {
            throw new IllegalArgumentException("Reason must be at most " + MAX_REASON + " characters");
        }
        this.deletedAt = now;
        this.deletedBy = by;
        this.deleteReason = why;
    }

    /// The bytes of a deleted photo were dropped.
    public void markPurged(Instant now) {
        this.purgedAt = now;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /// @throws StaleDataException when someone saved the photo since the caller read it
    public void requireVersion(long expectedVersion) {
        StaleDataException.check(expectedVersion, version);
    }

    private static @Nullable String clean(@Nullable String caption) {
        if (caption == null || caption.isBlank()) {
            return null;
        }
        String text = caption.strip();
        if (text.length() > MAX_CAPTION) {
            throw new InvalidPhotoInputException("caption", "Caption must be at most " + MAX_CAPTION + " characters");
        }
        return text;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is part of every Java runtime", e);
        }
    }

    public UUID id() {
        return id;
    }

    public UUID workOrderId() {
        return workOrderId;
    }

    public PhotoStage stage() {
        return stage;
    }

    public @Nullable String caption() {
        return caption;
    }

    public ImageType type() {
        return type;
    }

    public int sizeBytes() {
        return sizeBytes;
    }

    /// Hex SHA-256 of the stored bytes: the ETag.
    public String sha256() {
        return sha256;
    }

    public UUID uploadedBy() {
        return uploadedBy;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public @Nullable Instant deletedAt() {
        return deletedAt;
    }

    public @Nullable UUID deletedBy() {
        return deletedBy;
    }

    public @Nullable String deleteReason() {
        return deleteReason;
    }

    public @Nullable Instant purgedAt() {
        return purgedAt;
    }

    public long version() {
        return version;
    }
}

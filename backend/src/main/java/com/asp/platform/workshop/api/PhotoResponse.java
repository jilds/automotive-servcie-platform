package com.asp.platform.workshop.api;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.asp.platform.workshop.application.PhotoView;
import com.asp.platform.workshop.domain.PhotoStage;
import com.asp.platform.workshop.domain.WorkOrderPhoto;

/// A photo's metadata; its bytes are at `/api/v1/work-orders/{workOrderId}/photos/{id}/content`.
record PhotoResponse(UUID id, UUID workOrderId, PhotoStage stage, @Nullable String caption, String contentType,
        int sizeBytes, @Nullable String uploadedByName, UUID uploadedById, Instant createdAt, long version) {

    static PhotoResponse of(PhotoView view) {
        WorkOrderPhoto p = view.photo();
        return new PhotoResponse(p.id(), p.workOrderId(), p.stage(), p.caption(), p.type().contentType(),
                p.sizeBytes(), view.uploadedByName(), p.uploadedBy(), p.createdAt(), p.version());
    }
}

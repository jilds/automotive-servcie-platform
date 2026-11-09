package com.asp.platform.workshop.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.asp.platform.workshop.domain.PhotoStage;
import com.asp.platform.workshop.domain.WorkOrderPhoto;

/// `PUT /work-orders/{id}/photos/{photoId}`: the stage and caption as they should be (a blank caption removes it).
record PhotoUpdateRequest(
        @NotNull PhotoStage stage,
        @Nullable @Size(max = WorkOrderPhoto.MAX_CAPTION) String caption,
        @NotNull Long version) {
}

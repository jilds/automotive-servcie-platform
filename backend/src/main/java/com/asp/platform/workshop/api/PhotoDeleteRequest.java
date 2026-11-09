package com.asp.platform.workshop.api;

import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.asp.platform.workshop.domain.WorkOrderPhoto;

/// `DELETE /work-orders/{id}/photos/{photoId}` carries the reason: a blank or missing one answers 400
/// `reason-required` (checked by the photo).
record PhotoDeleteRequest(@Nullable @Size(max = WorkOrderPhoto.MAX_REASON) String reason) {
}

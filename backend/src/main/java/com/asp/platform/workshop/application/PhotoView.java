package com.asp.platform.workshop.application;

import org.jspecify.annotations.Nullable;

import com.asp.platform.workshop.domain.WorkOrderPhoto;

/// A photo's metadata with the name of who uploaded it.
public record PhotoView(WorkOrderPhoto photo, @Nullable String uploadedByName) {
}

package com.asp.platform.workshop.api;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/// A null mechanic unassigns the work order.
record AssignMechanicRequest(@Nullable UUID mechanicId) {
}

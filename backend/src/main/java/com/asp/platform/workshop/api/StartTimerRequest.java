package com.asp.platform.workshop.api;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/// `POST /work-orders/{id}/time/start`: the body is optional; `lineId` null (or no body) means the whole order.
record StartTimerRequest(@Nullable UUID lineId) {
}

package com.asp.platform.workshop.api;

import jakarta.validation.constraints.NotNull;

import com.asp.platform.workshop.domain.WorkOrderStatus;

record StatusRequest(@NotNull WorkOrderStatus status) {
}

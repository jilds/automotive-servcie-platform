package com.asp.platform.workshop.application;

import java.util.List;
import java.util.Map;

import com.asp.platform.workshop.domain.WorkOrderStatus;

/// The board's columns in workflow order, oldest status change first within a column.
public record Board(Map<WorkOrderStatus, List<BoardCard>> columns) {
}

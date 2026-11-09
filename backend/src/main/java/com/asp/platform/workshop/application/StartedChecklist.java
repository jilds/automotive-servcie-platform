package com.asp.platform.workshop.application;

/// The checklist after a start; `created` is false when it had been started before.
public record StartedChecklist(ChecklistView checklist, boolean created) {
}

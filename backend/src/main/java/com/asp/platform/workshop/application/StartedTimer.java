package com.asp.platform.workshop.application;

/// The running entry after a start; `created` is false when the same order and line were already running.
public record StartedTimer(TimeEntryView entry, boolean created) {
}

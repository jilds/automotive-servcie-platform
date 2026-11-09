package com.asp.platform.workshop.application;

import org.jspecify.annotations.Nullable;

import com.asp.platform.workshop.domain.TimeEntryChange;

/// A correction of a time entry with the name of who made it.
public record TimeEntryChangeView(TimeEntryChange change, @Nullable String changedByName) {
}

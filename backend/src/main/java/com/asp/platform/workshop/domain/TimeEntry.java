package com.asp.platform.workshop.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.asp.platform.shared.StaleDataException;

/// Time a mechanic worked on a work order, optionally on one of its lines. Running while `stoppedAt` is null; a
/// mechanic has at most one running entry (the database keeps that too). A correction keeps who, when and why of the
/// last edit.
public final class TimeEntry {

    /// The longest reason kept for a correction.
    public static final int MAX_REASON_LENGTH = 500;

    private final UUID id;
    private final UUID workOrderId;
    private @Nullable UUID lineId;
    private final UUID mechanicId;
    private Instant startedAt;
    private @Nullable Instant stoppedAt;
    private boolean autoStopped;
    private @Nullable UUID editedBy;
    private @Nullable Instant editedAt;
    private @Nullable String editReason;
    private final long version;

    public TimeEntry(UUID id, UUID workOrderId, @Nullable UUID lineId, UUID mechanicId, Instant startedAt,
            @Nullable Instant stoppedAt, boolean autoStopped, @Nullable UUID editedBy, @Nullable Instant editedAt,
            @Nullable String editReason, long version) {
        this.id = id;
        this.workOrderId = workOrderId;
        this.lineId = lineId;
        this.mechanicId = mechanicId;
        this.startedAt = startedAt;
        this.stoppedAt = stoppedAt;
        this.autoStopped = autoStopped;
        this.editedBy = editedBy;
        this.editedAt = editedAt;
        this.editReason = editReason;
        this.version = version;
    }

    /// A timer the mechanic (or the owner for them) starts now.
    public static TimeEntry start(UUID id, UUID workOrderId, @Nullable UUID lineId, UUID mechanicId, Instant now) {
        return new TimeEntry(id, workOrderId, lineId, mechanicId, now, null, false, null, null, null, 0);
    }

    /// A missed entry added afterwards by the owner or front desk, with the reason.
    ///
    /// @throws ReasonRequiredException   without a reason
    /// @throws InvalidTimeRangeException when a time is in the future or the stop is before the start
    public static TimeEntry missed(UUID id, UUID workOrderId, @Nullable UUID lineId, UUID mechanicId,
            Instant startedAt, Instant stoppedAt, @Nullable String reason, UUID editedBy, Instant now) {
        String why = requireReason(reason);
        checkRange(startedAt, stoppedAt, now);
        return new TimeEntry(id, workOrderId, lineId, mechanicId, startedAt, stoppedAt, false, editedBy, now, why, 0);
    }

    public boolean isRunning() {
        return stoppedAt == null;
    }

    /// Whole seconds worked; a running entry counts up to `now`.
    public long seconds(Instant now) {
        Instant end = stoppedAt == null ? now : stoppedAt;
        return Math.max(0, Duration.between(startedAt, end).getSeconds());
    }

    /// @param automatically true when moving the order or changing its mechanic stops it, not the mechanic
    /// @throws TimerNotRunningException when it is already stopped
    public void stop(Instant now, boolean automatically) {
        if (stoppedAt != null) {
            throw new TimerNotRunningException();
        }
        // A start a moment ahead of this clock (another instance) must not give a negative entry.
        stoppedAt = now.isBefore(startedAt) ? startedAt : now;
        autoStopped = automatically;
    }

    /// The owner or front desk replaces the times and the line of the entry. A running entry stays running when
    /// `newStop` is null.
    ///
    /// @throws ReasonRequiredException   without a reason
    /// @throws InvalidTimeRangeException when a time is in the future, the stop is before the start, or a stopped entry
    ///                                   is given no stop
    public void correct(Instant newStart, @Nullable Instant newStop, @Nullable UUID newLineId, @Nullable String reason,
            UUID by, Instant now) {
        String why = requireReason(reason);
        if (newStop == null && stoppedAt != null) {
            throw new InvalidTimeRangeException("stoppedAt", "A stopped entry needs a stop time");
        }
        checkRange(newStart, newStop, now);
        this.startedAt = newStart;
        if (newStop != null && stoppedAt == null) {
            this.autoStopped = false;
        }
        this.stoppedAt = newStop;
        this.lineId = newLineId;
        this.editedBy = by;
        this.editedAt = now;
        this.editReason = why;
    }

    /// @throws StaleDataException when someone saved the entry since the caller read it
    public void requireVersion(long expectedVersion) {
        StaleDataException.check(expectedVersion, version);
    }

    private static String requireReason(@Nullable String reason) {
        String why = reason == null ? "" : reason.strip();
        if (why.isEmpty()) {
            throw new ReasonRequiredException("Say why the time is corrected");
        }
        if (why.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException("Reason must be at most " + MAX_REASON_LENGTH + " characters");
        }
        return why;
    }

    private static void checkRange(Instant start, @Nullable Instant stop, Instant now) {
        if (start.isAfter(now)) {
            throw new InvalidTimeRangeException("startedAt", "The start cannot be in the future");
        }
        if (stop != null && stop.isAfter(now)) {
            throw new InvalidTimeRangeException("stoppedAt", "The stop cannot be in the future");
        }
        if (stop != null && stop.isBefore(start)) {
            throw new InvalidTimeRangeException("stoppedAt", "The stop cannot be before the start");
        }
    }

    public UUID id() {
        return id;
    }

    public UUID workOrderId() {
        return workOrderId;
    }

    public @Nullable UUID lineId() {
        return lineId;
    }

    public UUID mechanicId() {
        return mechanicId;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public @Nullable Instant stoppedAt() {
        return stoppedAt;
    }

    public boolean autoStopped() {
        return autoStopped;
    }

    public @Nullable UUID editedBy() {
        return editedBy;
    }

    public @Nullable Instant editedAt() {
        return editedAt;
    }

    public @Nullable String editReason() {
        return editReason;
    }

    public long version() {
        return version;
    }
}

package com.asp.platform.workshop.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.asp.platform.shared.CurrentUser;
import com.asp.platform.workshop.application.StartedTimer;
import com.asp.platform.workshop.application.TimeTrackingService;

/// Mechanic time on work orders. Everyone reads; the assigned mechanic and the owner run the timer; the owner and the
/// front desk correct entries. No prices and no customer data in any answer.
@RestController
@RequestMapping("/api/v1")
class TimeTrackingController {

    private final TimeTrackingService time;

    TimeTrackingController(TimeTrackingService time) {
        this.time = time;
    }

    @GetMapping("/work-orders/{id}/time")
    TimeSummaryResponse summary(CurrentUser user, @PathVariable UUID id) {
        return TimeSummaryResponse.of(time.summary(user, id));
    }

    /// 201 with the new running entry; 200 when the same order and line already run. The body is optional.
    @PostMapping("/work-orders/{id}/time/start")
    @PreAuthorize("hasAnyRole('OWNER','MECHANIC')")
    ResponseEntity<TimeEntryResponse> start(CurrentUser user, @PathVariable UUID id,
            @RequestBody(required = false) @Valid StartTimerRequest request) {
        StartedTimer started = time.start(user, id, request == null ? null : request.lineId());
        return ResponseEntity.status(started.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(TimeEntryResponse.of(started.entry()));
    }

    @PostMapping("/work-orders/{id}/time/stop")
    @PreAuthorize("hasAnyRole('OWNER','MECHANIC')")
    TimeEntryResponse stop(CurrentUser user, @PathVariable UUID id) {
        return TimeEntryResponse.of(time.stop(user, id));
    }

    /// The signed-in user's running entry, whatever the order.
    @GetMapping("/time/running")
    RunningTimerResponse running(CurrentUser user) {
        return RunningTimerResponse.of(time.running(user));
    }

    @PostMapping("/work-orders/{id}/time-entries")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
    TimeEntryResponse addMissed(CurrentUser user, @PathVariable UUID id,
            @Valid @RequestBody MissedTimeRequest request) {
        return TimeEntryResponse.of(time.addMissed(user, id, request.mechanicId(), request.lineId(),
                request.startedAt(), request.stoppedAt(), request.reason()));
    }

    /// The corrections of an entry (what it was before each, who, when, why), oldest first.
    @GetMapping("/work-orders/{id}/time-entries/{entryId}/changes")
    @PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
    List<TimeEntryChangeResponse> changes(CurrentUser user, @PathVariable UUID id,
            @PathVariable UUID entryId) {
        return time.changesOf(user, id, entryId).stream().map(TimeEntryChangeResponse::of).toList();
    }

    @PutMapping("/work-orders/{id}/time-entries/{entryId}")
    @PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
    TimeEntryResponse correct(CurrentUser user, @PathVariable UUID id, @PathVariable UUID entryId,
            @Valid @RequestBody TimeCorrectionRequest request) {
        return TimeEntryResponse.of(time.correct(user, id, entryId, request.startedAt(), request.stoppedAt(),
                request.lineId(), request.reason(), request.version()));
    }
}

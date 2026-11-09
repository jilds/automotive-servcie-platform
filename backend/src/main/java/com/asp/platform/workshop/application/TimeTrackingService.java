package com.asp.platform.workshop.application;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.asp.platform.customer.CustomerManagement;
import com.asp.platform.identity.WorkshopDirectory;
import com.asp.platform.shared.CurrentUser;
import com.asp.platform.workshop.domain.InvalidLineException;
import com.asp.platform.workshop.domain.InvalidMechanicException;
import com.asp.platform.workshop.domain.NoMechanicAssignedException;
import com.asp.platform.workshop.domain.TimeEntry;
import com.asp.platform.workshop.domain.TimeEntryChange;
import com.asp.platform.workshop.domain.TimeEntryChangeRepository;
import com.asp.platform.workshop.domain.TimeEntryNotFoundException;
import com.asp.platform.workshop.domain.TimeEntryRepository;
import com.asp.platform.workshop.domain.TimeTrackingClosedException;
import com.asp.platform.workshop.domain.TimerNotRunningException;
import com.asp.platform.workshop.domain.WorkOrder;
import com.asp.platform.workshop.domain.WorkOrderLine;
import com.asp.platform.workshop.domain.WorkOrderNotFoundException;
import com.asp.platform.workshop.domain.WorkOrderRepository;
import com.asp.platform.workshop.domain.WorkOrderStatus;

/// Mechanic time on work orders: the timer, its totals and the corrections.
///
/// A timer belongs to the order's assigned mechanic, who starts and stops it; the owner may do it for them. A mechanic
/// has at most one running entry: starting elsewhere stops the first. Every change locks the work order row first
/// ([WorkOrderRepository#lockById], 3 s then 409 `try-again`), as line changes and moves do, so a start cannot slip in
/// between a move to READY and its auto-stop.
@Service
public class TimeTrackingService {

    private final WorkOrderRepository orders;
    private final TimeEntryRepository entries;
    private final TimeEntryChangeRepository changes;
    private final WorkshopDirectory directory;
    private final CustomerManagement customers;
    private final Clock clock;

    TimeTrackingService(WorkOrderRepository orders, TimeEntryRepository entries, TimeEntryChangeRepository changes,
            WorkshopDirectory directory,
            CustomerManagement customers, Clock clock) {
        this.orders = orders;
        this.entries = entries;
        this.changes = changes;
        this.directory = directory;
        this.customers = customers;
        this.clock = clock;
    }

    /// Starts the timer of the order's assigned mechanic, on the order or on one of its lines. A running entry of the
    /// mechanic elsewhere (another order, or another line here) is stopped first; the same order and line again change
    /// nothing.
    ///
    /// @throws AccessDeniedException       for a mechanic who is not assigned, and for the front desk
    /// @throws NoMechanicAssignedException when the owner starts on an order nobody is assigned to
    /// @throws TimeTrackingClosedException when the order is READY or later
    /// @throws InvalidLineException        when `lineId` is not a line of the order
    @Transactional
    public StartedTimer start(CurrentUser user, UUID workOrderId, @Nullable UUID lineId) {
        WorkOrder order = lockedOrder(workOrderId);
        UUID mechanicId = timerOwner(user, order);
        if (!order.status().isEditable()) {
            throw new TimeTrackingClosedException(order.status());
        }
        requireLine(order, lineId);
        Instant now = clock.instant();
        entries.lockMechanic(mechanicId);
        Optional<TimeEntry> running = entries.findRunningByMechanic(mechanicId);
        if (running.isPresent()) {
            TimeEntry current = running.get();
            if (current.workOrderId().equals(workOrderId) && Objects.equals(current.lineId(), lineId)) {
                return new StartedTimer(view(user, current, now), false);
            }
            current.stop(now, false);
            entries.save(current);
        }
        TimeEntry started = entries.save(TimeEntry.start(UUID.randomUUID(), workOrderId, lineId, mechanicId, now));
        return new StartedTimer(view(user, started, now), true);
    }

    /// Stops the order's running timer.
    ///
    /// @throws AccessDeniedException    for a mechanic who is not assigned, and for the front desk
    /// @throws TimerNotRunningException when nothing runs on the order
    @Transactional
    public TimeEntryView stop(CurrentUser user, UUID workOrderId) {
        WorkOrder order = lockedOrder(workOrderId);
        entries.lockMechanic(timerOwner(user, order));
        Instant now = clock.instant();
        TimeEntry running = entries.findRunningByWorkOrder(workOrderId).stream().findFirst()
                .orElseThrow(TimerNotRunningException::new);
        running.stop(now, false);
        return view(user, entries.save(running), now);
    }

    /// Stops the running timer of the order at `now`, marked as stopped automatically. Called by the transaction that
    /// moves the order to READY or later or gives it to another mechanic, which holds the order's lock.
    @Transactional(propagation = Propagation.MANDATORY)
    public void stopRunning(UUID workOrderId, Instant now) {
        // Lock each mechanic first (found by a first read), then read again and stop, in the mechanic lock order.
        entries.findRunningByWorkOrder(workOrderId).stream().map(TimeEntry::mechanicId).distinct()
                .sorted().forEach(entries::lockMechanic);
        for (TimeEntry running : entries.findRunningByWorkOrder(workOrderId)) {
            running.stop(now, true);
            entries.save(running);
        }
    }

    /// The signed-in user's running entry on any order, with the order's number and plate.
    @Transactional(readOnly = true)
    public RunningTimer running(CurrentUser user) {
        Optional<TimeEntry> running = entries.findRunningByMechanic(user.userId());
        if (running.isEmpty()) {
            return RunningTimer.none();
        }
        TimeEntry entry = running.get();
        WorkOrder order = orders.findById(entry.workOrderId()).orElseThrow(WorkOrderNotFoundException::new);
        String plate = customers.findVehicle(order.vehicleId()).map(v -> v.plateDisplay()).orElse(null);
        return new RunningTimer(view(user, entry, clock.instant()), order.number(), plate);
    }

    /// The entries and totals of the order, at the time of the call.
    ///
    /// @throws WorkOrderNotFoundException for an unknown order
    @Transactional(readOnly = true)
    public TimeSummary summary(CurrentUser user, UUID workOrderId) {
        WorkOrder order = orders.findById(workOrderId).orElseThrow(WorkOrderNotFoundException::new);
        Instant now = clock.instant();
        Map<UUID, String> names = directory.memberNames(user.tenantId());
        List<TimeEntryView> views = entries.findByWorkOrder(workOrderId).stream()
                .map(entry -> TimeEntryView.of(entry, names, now)).toList();
        Set<UUID> lineIds = order.lines().stream().map(WorkOrderLine::id).collect(Collectors.toSet());
        Map<UUID, Long> perLine = new LinkedHashMap<>();
        long unassigned = 0;
        long total = 0;
        for (TimeEntryView view : views) {
            total += view.seconds();
            UUID lineId = view.entry().lineId();
            if (lineId != null && lineIds.contains(lineId)) {
                perLine.merge(lineId, view.seconds(), Long::sum);
            } else {
                unassigned += view.seconds();
            }
        }
        TimeEntryView running = views.stream().filter(v -> v.entry().isRunning()).findFirst().orElse(null);
        List<TimeSummary.LineSeconds> byLine = perLine.entrySet().stream()
                .map(e -> new TimeSummary.LineSeconds(e.getKey(), e.getValue())).toList();
        return new TimeSummary(now, total, unassigned, byLine, running, views);
    }

    /// The owner or front desk replaces the times and line of an entry, with the reason.
    ///
    /// @throws TimeEntryNotFoundException                                        for an entry of another order
    /// @throws com.asp.platform.shared.StaleDataException                        for another stored version
    /// @throws com.asp.platform.workshop.domain.ReasonRequiredException          without a reason
    /// @throws com.asp.platform.workshop.domain.InvalidTimeRangeException        for a time in the future or a stop
    ///                                                                           before the start
    @Transactional
    public TimeEntryView correct(CurrentUser user, UUID workOrderId, UUID entryId, Instant startedAt,
            @Nullable Instant stoppedAt, @Nullable UUID lineId, @Nullable String reason, long expectedVersion) {
        WorkOrder order = lockedOrder(workOrderId);
        entries.findById(entryId).filter(e -> e.workOrderId().equals(workOrderId))
                .ifPresent(e -> entries.lockMechanic(e.mechanicId()));
        TimeEntry entry = entries.findById(entryId).filter(e -> e.workOrderId().equals(workOrderId))
                .orElseThrow(TimeEntryNotFoundException::new);
        entry.requireVersion(expectedVersion);
        requireLine(order, lineId);
        Instant now = clock.instant();
        TimeEntry before = new TimeEntry(entry.id(), entry.workOrderId(), entry.lineId(), entry.mechanicId(),
                entry.startedAt(), entry.stoppedAt(), entry.autoStopped(), entry.editedBy(), entry.editedAt(),
                entry.editReason(), entry.version());
        entry.correct(startedAt, stoppedAt, lineId, reason, user.userId(), now);
        changes.save(new TimeEntryChange(UUID.randomUUID(), entryId, before.startedAt(), before.stoppedAt(),
                before.lineId(), user.userId(), now, entry.editReason()));
        return view(user, entries.save(entry), now);
    }

    /// The corrections of an entry, oldest first.
    ///
    /// @throws TimeEntryNotFoundException for an entry of another order
    @Transactional(readOnly = true)
    public List<TimeEntryChangeView> changesOf(CurrentUser user, UUID workOrderId, UUID entryId) {
        orders.findById(workOrderId).orElseThrow(WorkOrderNotFoundException::new);
        entries.findById(entryId).filter(e -> e.workOrderId().equals(workOrderId))
                .orElseThrow(TimeEntryNotFoundException::new);
        Map<UUID, String> names = directory.memberNames(user.tenantId());
        return changes.findByEntry(entryId).stream().map(c -> new TimeEntryChangeView(c, names.get(c.changedBy())))
                .toList();
    }

    /// Adds a stopped entry the mechanic forgot to record, for an active mechanic of the workshop.
    ///
    /// @throws com.asp.platform.workshop.domain.InvalidMechanicException when `mechanicId` is not one
    @Transactional
    public TimeEntryView addMissed(CurrentUser user, UUID workOrderId, UUID mechanicId, @Nullable UUID lineId,
            Instant startedAt, Instant stoppedAt, @Nullable String reason) {
        WorkOrder order = lockedOrder(workOrderId);
        entries.lockMechanic(mechanicId);
        if (!directory.isActiveMechanic(user.tenantId(), mechanicId)) {
            throw new InvalidMechanicException("mechanicId");
        }
        requireLine(order, lineId);
        Instant now = clock.instant();
        TimeEntry missed = TimeEntry.missed(UUID.randomUUID(), workOrderId, lineId, mechanicId, startedAt, stoppedAt,
                reason, user.userId(), now);
        return view(user, entries.save(missed), now);
    }

    /// The mechanic whose timer it is: the assigned mechanic themselves, or for the owner the assigned mechanic.
    private static UUID timerOwner(CurrentUser user, WorkOrder order) {
        if (user.isMechanic()) {
            if (!order.isAssignedTo(user.userId())) {
                throw new AccessDeniedException("Only the assigned mechanic can start or stop the timer");
            }
            return user.userId();
        }
        if (user.isOwner()) {
            UUID assigned = order.assignedMechanicId();
            if (assigned == null) {
                throw new NoMechanicAssignedException();
            }
            return assigned;
        }
        throw new AccessDeniedException("The front desk corrects time but does not run timers");
    }

    private static void requireLine(WorkOrder order, @Nullable UUID lineId) {
        if (lineId != null && order.lines().stream().noneMatch(line -> line.id().equals(lineId))) {
            throw new InvalidLineException();
        }
    }

    private WorkOrder lockedOrder(UUID id) {
        return orders.lockById(id).orElseThrow(WorkOrderNotFoundException::new);
    }

    private TimeEntryView view(CurrentUser user, TimeEntry entry, Instant now) {
        Map<UUID, String> names = new HashMap<>(directory.memberNames(user.tenantId()));
        return TimeEntryView.of(entry, names, now);
    }
}

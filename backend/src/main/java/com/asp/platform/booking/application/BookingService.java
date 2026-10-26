package com.asp.platform.booking.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.asp.platform.booking.BookingStatusChanged;
import com.asp.platform.booking.domain.Booking;
import com.asp.platform.booking.domain.BookingNotFoundException;
import com.asp.platform.booking.domain.BookingRangeTooLongException;
import com.asp.platform.booking.domain.BookingRepository;
import com.asp.platform.booking.domain.BookingRules;
import com.asp.platform.booking.domain.BookingSettings;
import com.asp.platform.booking.domain.BookingSettingsRepository;
import com.asp.platform.booking.domain.BookingStatus;
import com.asp.platform.booking.domain.SlotCalendar;
import com.asp.platform.booking.domain.SlotUnavailableException;
import com.asp.platform.customer.CustomerManagement;
import com.asp.platform.customer.VehicleSummary;
import com.asp.platform.identity.WorkshopDirectory;

/// The front desk's bookings: the agenda, the decisions (confirm, decline, move, no-show) and the count waiting for
/// a decision. Row-level security keeps every query inside the signed-in workshop, so another workshop's booking
/// reads as not found. Every decision needs the version the front desk read (409 `stale-data`).
@Service
public class BookingService {

    /// Days the agenda shows when the caller gives no end date.
    static final int DEFAULT_DAYS = 7;

    private final BookingRepository bookings;
    private final BookingSettingsRepository settings;
    private final CustomerManagement customers;
    private final WorkshopDirectory directory;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    BookingService(BookingRepository bookings, BookingSettingsRepository settings, CustomerManagement customers,
            WorkshopDirectory directory, ApplicationEventPublisher events, Clock clock) {
        this.bookings = bookings;
        this.settings = settings;
        this.customers = customers;
        this.directory = directory;
        this.events = events;
        this.clock = clock;
    }

    /// Tells the customer after the commit, when they left an e-mail address ([BookingStatusChanged]).
    private Booking announced(UUID tenantId, Booking booking, BookingStatusChanged.Change change) {
        String email = booking.contact().email();
        if (email != null) {
            events.publishEvent(new BookingStatusChanged(tenantId, booking.number(), booking.slotStart(),
                    booking.contact().name(), email, change,
                    change == BookingStatusChanged.Change.DECLINED ? booking.declineReason() : null));
        }
        return booking;
    }

    /// Bookings whose slot is on the days `from` to `to` (both included, in the workshop's time zone), ordered by
    /// slot. Defaults: today to today + 7 days. At most 124 days apart (400 `validation` on `to`).
    @Transactional(readOnly = true)
    public List<BookingView> list(UUID tenantId, @Nullable LocalDate from, @Nullable LocalDate to,
            @Nullable BookingStatus status) {
        ZoneId zone = directory.timezone(tenantId);
        LocalDate first = from != null ? from : LocalDate.now(clock.withZone(zone));
        LocalDate last = to != null ? to : first.plusDays(DEFAULT_DAYS);
        if (last.isBefore(first)) {
            return List.of();
        }
        if (ChronoUnit.DAYS.between(first, last) > BookingRangeTooLongException.MAX_DAYS) {
            throw new BookingRangeTooLongException();
        }
        List<Booking> found = bookings.findBetween(first.atStartOfDay(zone).toInstant(),
                last.plusDays(1).atStartOfDay(zone).toInstant(), status);
        return views(tenantId, found);
    }

    @Transactional(readOnly = true)
    public BookingView get(UUID tenantId, UUID id) {
        return view(tenantId, booking(id));
    }

    /// REQUESTED → CONFIRMED.
    @Transactional
    public BookingView confirm(UUID tenantId, UUID userId, UUID id, long expectedVersion) {
        Booking booking = booking(id);
        booking.requireVersion(expectedVersion);
        booking.confirm(userId, clock.instant());
        return view(tenantId, announced(tenantId, bookings.save(booking), BookingStatusChanged.Change.CONFIRMED));
    }

    /// REQUESTED → DECLINED with a reason (400 `decline-reason-required` without one).
    @Transactional
    public BookingView decline(UUID tenantId, UUID userId, UUID id, @Nullable String reason, long expectedVersion) {
        Booking booking = booking(id);
        booking.requireVersion(expectedVersion);
        booking.decline(reason, userId, clock.instant());
        return view(tenantId, announced(tenantId, bookings.save(booking), BookingStatusChanged.Change.DECLINED));
    }

    /// Moves the booking to another slot of the opening hours that has not started and has a place left, and
    /// confirms it. The front desk is not bound by the lead time or the horizon of the public page. Takes the
    /// settings lock like a new booking, so a move and a public request cannot both take the last place.
    ///
    /// @throws SlotUnavailableException when the slot is not a slot of the opening hours, has started or is full
    @Transactional
    public BookingView move(UUID tenantId, UUID userId, UUID id, Instant slotStart, long expectedVersion) {
        Booking booking = booking(id);
        booking.requireVersion(expectedVersion);
        Instant now = clock.instant();
        BookingSettings current = settings.lockOrCreate(BookingRules.defaults());
        SlotCalendar calendar = new SlotCalendar(current.rules(), directory.timezone(tenantId));
        // Exactly what [#slots] lists: a future slot of the opening hours with a place left (its own place counts
        // as taken, so a move to the same full slot is refused; confirm it instead).
        if (!calendar.isSlot(slotStart, now) || bookings.placesTakenAt(slotStart) >= current.rules().carsPerSlot()) {
            throw new SlotUnavailableException();
        }
        booking.moveTo(slotStart, userId, now);
        return view(tenantId, announced(tenantId, bookings.save(booking), BookingStatusChanged.Change.MOVED));
    }

    /// CONFIRMED → NO_SHOW once the slot has started.
    @Transactional
    public BookingView noShow(UUID tenantId, UUID userId, UUID id, long expectedVersion) {
        Booking booking = booking(id);
        booking.requireVersion(expectedVersion);
        booking.noShow(userId, clock.instant());
        return view(tenantId, bookings.save(booking));
    }

    /// The free slots the front desk may move a booking to, `days` days from `from` (default today in the workshop's
    /// zone): every future slot of the opening hours on a day that is not closed, with a place left. Lead time and
    /// horizon do not apply. [#move] accepts exactly these.
    @Transactional(readOnly = true)
    public List<SlotCalendar.Day> slots(UUID tenantId, @Nullable LocalDate from, int days) {
        ZoneId zone = directory.timezone(tenantId);
        BookingRules rules = settings.find().map(BookingSettings::rules).orElseGet(BookingRules::defaults);
        SlotCalendar calendar = new SlotCalendar(rules, zone);
        Instant now = clock.instant();
        LocalDate first = from != null ? from : calendar.today(now);
        SlotCalendar.Span span = calendar.span(first, days);
        return calendar.staffDays(first, days, now, bookings.placesTaken(span.from(), span.to()));
    }

    /// Bookings waiting for a decision (REQUESTED), for the dashboard tile.
    @Transactional(readOnly = true)
    public long pendingCount() {
        return bookings.countRequested();
    }

    private Booking booking(UUID id) {
        return bookings.findById(id).orElseThrow(BookingNotFoundException::new);
    }

    private BookingView view(UUID tenantId, Booking booking) {
        return views(tenantId, List.of(booking)).getFirst();
    }

    /// The vehicles by plate in one query, and the names of who decided.
    private List<BookingView> views(UUID tenantId, List<Booking> found) {
        if (found.isEmpty()) {
            return List.of();
        }
        Map<String, VehicleSummary> vehicles = customers.findVehiclesByPlates(found.stream()
                .filter(b -> b.vehicleId() == null).map(b -> b.contact().plate()).distinct().toList());
        Map<UUID, String> names = found.stream().anyMatch(b -> b.decidedBy() != null)
                ? directory.memberNames(tenantId) : Map.of();
        return found.stream().map(b -> {
            UUID vehicleId = b.vehicleId();
            if (vehicleId == null) {
                VehicleSummary vehicle = vehicles.get(b.contact().plate());
                vehicleId = vehicle == null ? null : vehicle.id();
            }
            UUID decidedBy = b.decidedBy();
            return new BookingView(b, vehicleId, decidedBy == null ? null : names.get(decidedBy));
        }).toList();
    }
}

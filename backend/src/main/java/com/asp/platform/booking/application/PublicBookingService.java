package com.asp.platform.booking.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.asp.platform.booking.domain.BookedService;
import com.asp.platform.booking.domain.Booking;
import com.asp.platform.booking.domain.BookingContact;
import com.asp.platform.booking.domain.BookingNotFoundException;
import com.asp.platform.booking.domain.BookingRepository;
import com.asp.platform.booking.domain.BookingRules;
import com.asp.platform.booking.domain.BookingServiceNotFoundException;
import com.asp.platform.booking.domain.BookingSettings;
import com.asp.platform.booking.domain.BookingSettingsRepository;
import com.asp.platform.booking.domain.SlotCalendar;
import com.asp.platform.booking.domain.SlotUnavailableException;
import com.asp.platform.catalogue.CatalogueLookup;
import com.asp.platform.catalogue.ServiceOffer;
import com.asp.platform.customer.CustomerManagement;
import com.asp.platform.shared.StatusTokens;

/// The booking pages of a workshop's public site. Runs inside the public request's
/// [com.asp.platform.shared.TenantScope], so row-level security keeps every query inside the workshop of the slug.
///
/// A new booking takes the workshop's settings lock, then checks and counts the slot and inserts the booking in the
/// same transaction: two requests for the last place of a slot give one booking and one 409 `slot-unavailable`.
@Service
public class PublicBookingService {

    /// Days the slot list may cover in one request.
    public static final int MAX_DAYS = 14;

    private final BookingRepository bookings;
    private final BookingSettingsRepository settings;
    private final CatalogueLookup catalogue;
    private final CustomerManagement customers;
    private final Clock clock;

    PublicBookingService(BookingRepository bookings, BookingSettingsRepository settings, CatalogueLookup catalogue,
            CustomerManagement customers, Clock clock) {
        this.bookings = bookings;
        this.settings = settings;
        this.catalogue = catalogue;
        this.customers = customers;
        this.clock = clock;
    }

    /// Active services listed online, at their price now.
    @Transactional(readOnly = true)
    public List<ServiceOffer> services() {
        return catalogue.onlineServices();
    }

    /// The free slots of `days` days from `from` (default: today in the workshop's zone).
    @Transactional(readOnly = true)
    public List<SlotCalendar.Day> slots(ZoneId zone, @Nullable LocalDate from, int days) {
        SlotCalendar calendar = new SlotCalendar(rules(), zone);
        LocalDate first = from != null ? from : calendar.today(clock.instant());
        SlotCalendar.Span span = calendar.span(first, days);
        return calendar.days(first, days, clock.instant(), bookings.placesTaken(span.from(), span.to()));
    }

    /// Stores a REQUESTED booking with a snapshot of the services, and returns the status token once.
    ///
    /// @throws BookingServiceNotFoundException when a service is not offered online (404 `service-not-found`)
    /// @throws SlotUnavailableException        when the slot is not offered or has no place left
    @Transactional
    public BookingReceipt book(ZoneId zone, BookingRequestCommand command) {
        List<BookedService> booked = bookedServices(command.serviceIds());
        BookingContact contact = new BookingContact(command.name(), command.phone(), command.email(),
                customers.normalizePlate(command.plate()), command.note());
        Instant now = clock.instant();
        BookingSettings current = settings.lockOrCreate(BookingRules.defaults());
        SlotCalendar calendar = new SlotCalendar(current.rules(), zone);
        if (!calendar.offers(command.slotStart(), now)
                || bookings.placesTakenAt(command.slotStart()) >= current.rules().carsPerSlot()) {
            throw new SlotUnavailableException();
        }
        String token = StatusTokens.generate();
        Booking saved = bookings.save(Booking.request(UUID.randomUUID(), bookings.nextNumber(), command.slotStart(),
                contact, booked, command.marketingConsent(), StatusTokens.hash(token), now));
        return new BookingReceipt(saved.number(), token, saved.slotStart());
    }

    /// @throws BookingNotFoundException for an unknown token
    @Transactional(readOnly = true)
    public PublicBookingView status(String token, String workshopName, ZoneId zone) {
        Booking booking = byToken(token);
        return new PublicBookingView(booking, workshopName, zone,
                booking.canCancel(clock.instant(), rules().cancelUntilHours()));
    }

    /// The customer cancels on the status link.
    ///
    /// @throws BookingNotFoundException                                     for an unknown token
    /// @throws com.asp.platform.booking.domain.BookingCancelTooLateException after the deadline
    /// @throws com.asp.platform.booking.domain.IllegalBookingStatusException when it no longer holds a place
    @Transactional
    public PublicBookingView cancel(String token, String workshopName, ZoneId zone) {
        Booking booking = byToken(token);
        booking.cancelByCustomer(clock.instant(), rules().cancelUntilHours());
        return new PublicBookingView(bookings.save(booking), workshopName, zone, false);
    }

    private Booking byToken(String token) {
        return bookings.findByTokenHash(StatusTokens.hash(token)).orElseThrow(BookingNotFoundException::new);
    }

    private BookingRules rules() {
        return settings.find().map(BookingSettings::rules).orElseGet(BookingRules::defaults);
    }

    /// Each service must be active and listed online; a service named twice is booked once.
    private List<BookedService> bookedServices(List<UUID> serviceIds) {
        List<UUID> ids = serviceIds.stream().distinct().toList();
        Map<UUID, ServiceOffer> offers = catalogue.services(ids);
        List<BookedService> booked = new ArrayList<>(ids.size());
        for (UUID id : ids) {
            ServiceOffer offer = offers.get(id);
            if (offer == null || !offer.bookableOnline()) {
                throw new BookingServiceNotFoundException();
            }
            booked.add(new BookedService(offer.id(), offer.name(), offer.durationMinutes(), offer.priceCents(),
                    offer.vatRate()));
        }
        return List.copyOf(booked);
    }
}

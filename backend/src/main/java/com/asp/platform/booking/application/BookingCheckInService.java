package com.asp.platform.booking.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.asp.platform.booking.BookingCheckIns;
import com.asp.platform.booking.domain.BookedService;
import com.asp.platform.booking.domain.Booking;
import com.asp.platform.booking.domain.BookingNotFoundException;
import com.asp.platform.booking.domain.BookingRepository;
import com.asp.platform.catalogue.CatalogueLookup;
import com.asp.platform.catalogue.ServiceOffer;

/// The check-in from a booking, for the workshop module. Joins the caller's transaction.
@Service
class BookingCheckInService implements BookingCheckIns {

    private final BookingRepository bookings;
    private final CatalogueLookup catalogue;

    BookingCheckInService(BookingRepository bookings, CatalogueLookup catalogue) {
        this.bookings = bookings;
        this.catalogue = catalogue;
    }

    @Override
    @Transactional
    public List<UUID> claim(UUID bookingId, UUID vehicleId) {
        Booking booking = booking(bookingId);
        booking.claimFor(vehicleId);
        Booking saved = bookings.save(booking);
        List<UUID> booked = saved.services().stream().map(BookedService::serviceItemId).toList();
        Map<UUID, ServiceOffer> offers = catalogue.services(booked);
        return booked.stream().filter(id -> {
            ServiceOffer offer = offers.get(id);
            return offer != null && offer.active();
        }).toList();
    }

    @Override
    @Transactional
    public void markCheckedIn(UUID bookingId, UUID workOrderId) {
        Booking booking = booking(bookingId);
        booking.checkedIn(workOrderId);
        bookings.save(booking);
    }

    private Booking booking(UUID id) {
        return bookings.findById(id).orElseThrow(BookingNotFoundException::new);
    }
}

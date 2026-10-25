/// Online booking (add-on `booking`, ADR 0004): the workshop's opening hours, slot length and capacity, the free
/// drop-off slots shown on its public page, the bookings customers request there and the front desk confirms,
/// declines or moves, and the check-in from a booking.
/// Public API: [BookingCheckIns], used by the workshop module to open a work order from a confirmed booking. Uses
/// the identity module through [com.asp.platform.identity.WorkshopDirectory] (time zone, names of who decided), the
/// catalogue through [com.asp.platform.catalogue.CatalogueLookup] (services listed online, their prices and
/// durations) and the customer module through [com.asp.platform.customer.CustomerManagement] (plate normalisation,
/// the workshop's vehicle with the booked plate). Never uses the workshop module.
@ApplicationModule(displayName = "Booking")
@NullMarked
package com.asp.platform.booking;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;

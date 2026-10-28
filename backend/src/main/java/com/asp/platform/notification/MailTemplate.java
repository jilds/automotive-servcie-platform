package com.asp.platform.notification;

import java.util.List;

/// The mails ASP sends. Each has `mail/<file>.html` and `mail/<file>.txt` and the subject `<file>.subject` in
/// `mail/messages*.properties`, whose `{0}`, `{1}`… are the model values named by [#subjectArguments].
public enum MailTemplate {

    /// To a new user: set your password. Model: `name`, `workshop`, `link`, `expires`, `firstOwner`.
    ACTIVATION("activation", "workshop"),
    /// To a customer who asked for a booking. Model: `name`, `workshop`, `number`, `when`, `link`.
    BOOKING_RECEIVED("booking-received", "workshop", "number"),
    /// Model: `name`, `workshop`, `number`, `when`.
    BOOKING_CONFIRMED("booking-confirmed", "workshop", "number"),
    /// Model: `name`, `workshop`, `number`, `when` (the new slot).
    BOOKING_MOVED("booking-moved", "workshop", "number"),
    /// Model: `name`, `workshop`, `number`, `when`, `reason`.
    BOOKING_DECLINED("booking-declined", "workshop", "number"),
    /// To each platform admin: a demo request from the landing site. Model: `name`, `workshop`, `phone`, `email`,
    /// `district`, `vehicleType` (`CAR`, `MOTORCYCLE`, `BOTH`), `message` (empty strings when not given), `link`.
    LEAD_RECEIVED("lead-received", "workshop"),
    /// To a customer who placed a shop order. Model: `name`, `workshop`, `number`, `total` (formatted), `until`,
    /// `link`.
    SHOP_ORDER_RECEIVED("shop-order-received", "workshop", "number"),
    /// Model: `name`, `workshop`, `number`, `until` (the new hold), `pickupNote` (empty when none).
    SHOP_ORDER_READY("shop-order-ready", "workshop", "number"),
    /// Cancelled by the workshop. Model: `name`, `workshop`, `number`, `reason`.
    SHOP_ORDER_CANCELLED("shop-order-cancelled", "workshop", "number"),
    /// Not collected in time. Model: `name`, `workshop`, `number`, `until`.
    SHOP_ORDER_EXPIRED("shop-order-expired", "workshop", "number"),
    /// To the customer of a vehicle: a quote to approve. Model: `name`, `workshop`, `number` (the work order's),
    /// `plate`, `total` (formatted, VAT included), `until`, `link`.
    QUOTE_SENT("quote-sent", "workshop", "number");

    private final String file;
    private final List<String> subjectArguments;

    MailTemplate(String file, String... subjectArguments) {
        this.file = file;
        this.subjectArguments = List.of(subjectArguments);
    }

    public String file() {
        return file;
    }

    public List<String> subjectArguments() {
        return subjectArguments;
    }
}

package com.asp.platform.booking.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.asp.platform.booking.application.BookingRequestCommand;
import com.asp.platform.booking.domain.BookingContact;
import com.asp.platform.shared.PhoneOrEmail;

/// `POST /public/{slug}/booking/bookings`: 1 to 10 services, a slot, the contact (at least a phone or an e-mail),
/// the plate (normalised like vehicles), an optional note of up to 500 characters, the consents and the hidden
/// `website` field, which people leave empty. `language` (optional) is the page language, for the e-mail with the
/// status link.
@PhoneOrEmail
record PublicBookingRequest(
        @NotEmpty @Size(max = 10) List<@NotNull UUID> serviceIds,
        @NotNull Instant slotStart,
        // The name goes into the mail to the given address, so a link in it would turn the form into a phishing relay.
        @NotBlank @Size(max = BookingContact.MAX_NAME)
        @Pattern(regexp = "(?is)(?!.*(://|www\\.|<|>)).*", message = "A name has no links") String name,
        @Nullable @Size(max = BookingContact.MAX_PHONE) String phone,
        @Nullable @Email @Size(max = BookingContact.MAX_EMAIL) String email,
        @NotBlank @Size(max = BookingContact.MAX_PLATE)
        @Pattern(regexp = ".*\\p{Alnum}.*", message = "A plate has letters or digits") String plate,
        @Nullable @Size(max = BookingContact.MAX_NOTE) String note,
        @Nullable Boolean privacyAccepted,
        @Nullable Boolean marketingConsent,
        @Nullable @Size(max = 500) String website,
        @Nullable @Pattern(regexp = "pt-PT|en|pt-BR", message = "pt-PT, en or pt-BR") String language)
        implements PhoneOrEmail.Subject {

    boolean honeypotFilled() {
        String w = website;
        return w != null && !w.isBlank();
    }

    boolean acceptedPrivacy() {
        return Boolean.TRUE.equals(privacyAccepted);
    }

    BookingRequestCommand toCommand() {
        return new BookingRequestCommand(serviceIds, slotStart, name, phone, email, plate, note,
                Boolean.TRUE.equals(marketingConsent), language);
    }
}

package com.asp.platform.identity.domain;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.asp.platform.shared.StaleDataException;

/// A demo request from the landing site: a workshop that is not a customer yet asks the ASP team to get in touch.
/// Platform-level, like [PlatformAdmin]: it belongs to no workshop.
///
/// @param phone              at least one of phone and e-mail is given
/// @param district           where the workshop is, as typed or picked on the site, or null
/// @param privacyAcceptedAt  when the person accepted the privacy notice (required to send the form)
/// @param statusChangedAt    when the platform admin last moved it, or null while NEW
/// @param version            how many times the stored row has changed; status changes name the version they read
public record Lead(UUID id, String name, String workshopName, @Nullable String phone, @Nullable String email,
        @Nullable String district, VehicleType vehicleType, @Nullable String message, Instant privacyAcceptedAt,
        boolean marketingConsent, Status status, Instant createdAt, @Nullable Instant statusChangedAt, long version) {

    public static final int MAX_NAME = 120;
    public static final int MAX_PHONE = 30;
    public static final int MAX_EMAIL = 254;
    public static final int MAX_DISTRICT = 60;
    public static final int MAX_MESSAGE = 2000;

    /// What the workshop services.
    public enum VehicleType {
        CAR,
        MOTORCYCLE,
        BOTH
    }

    /// Where the platform admin stands with the request. Any move is allowed: a closed request may be reopened.
    public enum Status {
        NEW,
        CONTACTED,
        CLOSED
    }

    public Lead {
        if ((phone == null || phone.isBlank()) && (email == null || email.isBlank())) {
            throw new IllegalArgumentException("A lead needs a phone or an e-mail");
        }
    }

    /// A request just sent from the site.
    public static Lead received(UUID id, String name, String workshopName, @Nullable String phone,
            @Nullable String email, @Nullable String district, VehicleType vehicleType, @Nullable String message,
            boolean marketingConsent, Instant now) {
        return new Lead(id, name, workshopName, phone, email, district, vehicleType, message, now, marketingConsent,
                Status.NEW, now, null, 0);
    }

    /// @throws StaleDataException when someone else changed it since the admin read it
    public Lead withStatus(Status newStatus, long expectedVersion, Instant now) {
        StaleDataException.check(expectedVersion, version);
        if (newStatus == status) {
            return this;
        }
        return new Lead(id, name, workshopName, phone, email, district, vehicleType, message, privacyAcceptedAt,
                marketingConsent, newStatus, createdAt, now, version);
    }

    @Override
    public String toString() {
        // Contact data stays out of logs.
        return "Lead[id=" + id + ", status=" + status + "]";
    }
}

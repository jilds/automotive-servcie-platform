package com.asp.platform.identity;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/// Published when the landing site's form stores a demo request; listeners run after the commit (the notification
/// module tells the platform admins). Carries the contact data for that mail only; never log it. The free-text
/// message stays out of the mail (it could carry a phishing link), so only whether there is one travels.
///
/// @param vehicleType `CAR`, `MOTORCYCLE` or `BOTH`
/// @param hasMessage  true when the request has a message to read in `/admin/leads`
public record LeadReceived(UUID leadId, String name, String workshopName, @Nullable String phone,
        @Nullable String email, @Nullable String district, String vehicleType, boolean hasMessage) {

    @Override
    public String toString() {
        return "LeadReceived[leadId=" + leadId + "]";
    }
}

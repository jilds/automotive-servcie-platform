package com.asp.platform.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.asp.platform.identity.application.LeadCommand;
import com.asp.platform.identity.domain.Lead;
import com.asp.platform.shared.PhoneOrEmail;

/// `POST /public-site/leads`: who asks, their workshop, a phone or an e-mail (or both), the district, what the
/// workshop services, an optional message, the consents and the hidden `website` field people leave empty. The
/// names and the district go into the ASP team's notification mail (the workshop name in its subject), so they are
/// one line without links, control characters or `<` `>`. The message stays out of the mail.
@PhoneOrEmail
record LeadRequest(
        @NotBlank @Size(max = Lead.MAX_NAME) @Pattern(regexp = ONE_LINE_NO_LINK, message = "One line, no links")
        String name,
        @NotBlank @Size(max = Lead.MAX_NAME) @Pattern(regexp = ONE_LINE_NO_LINK, message = "One line, no links")
        String workshopName,
        @Nullable @Size(max = Lead.MAX_PHONE) @Pattern(regexp = "[+0-9 ()-]*", message = "Digits, spaces, + ( ) -")
        String phone,
        @Nullable @Email @Size(max = Lead.MAX_EMAIL) String email,
        @Nullable @Size(max = Lead.MAX_DISTRICT) @Pattern(regexp = ONE_LINE_NO_LINK, message = "One line, no links")
        String district,
        @NotNull Lead.VehicleType vehicleType,
        @Nullable @Size(max = Lead.MAX_MESSAGE) String message,
        @Nullable Boolean privacyAccepted,
        @Nullable Boolean marketingConsent,
        @Nullable @Size(max = 500) String website) implements PhoneOrEmail.Subject {

    // (?s): the look-ahead must also see past a line or paragraph separator. \p{Cntrl} is ASCII only, so the C1
    // controls (NEL among them) are listed too; site/src/scripts/lead-validation.ts mirrors this.
    static final String ONE_LINE_NO_LINK = "(?is)(?!.*(://|www\\.))[^\\p{Cntrl}\\u0080-\\u009f\\u2028\\u2029<>]*";

    boolean honeypotFilled() {
        String w = website;
        return w != null && !w.isBlank();
    }

    boolean acceptedPrivacy() {
        return Boolean.TRUE.equals(privacyAccepted);
    }

    LeadCommand toCommand() {
        return new LeadCommand(name.trim(), workshopName.trim(), blankToNull(phone), blankToNull(email),
                blankToNull(district), vehicleType, blankToNull(message), Boolean.TRUE.equals(marketingConsent));
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

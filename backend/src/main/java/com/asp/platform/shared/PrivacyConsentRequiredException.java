package com.asp.platform.shared;

/// A public form (booking, shop order) sent without accepting the privacy notice: nothing is stored.
public class PrivacyConsentRequiredException extends InvalidInputException {

    public static final String CODE = "privacy-consent-required";
    public static final String FIELD = "privacyAccepted";

    public PrivacyConsentRequiredException() {
        super(CODE, FIELD, "Accept the privacy notice to send this form");
    }
}

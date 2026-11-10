package com.asp.platform.billing.domain;

import java.time.Instant;

/// What the provider assigned to a document it issued. Each value is bounded before it can reach the database: an
/// over-long one is a refusal of the provider's answer (`provider-invalid-response`), so the document becomes FAILED
/// instead of the save failing.
///
/// @param providerDocumentId the provider's id, kept to void or fetch the document later
/// @param fullNumber         type, series and sequence as printed, for example "FT SANDBOX/1"
/// @param totalCents         the total the provider issued, compared with ours before the document counts as issued
public record IssuedDocument(String providerDocumentId, String series, int sequence, String fullNumber, String atcud,
        String qrPayload, String hashFragment, Instant issuedAt, long totalCents) {

    public static final String INVALID_RESPONSE = "provider-invalid-response";
    static final int MAX_TEXT = 100;
    static final int MAX_QR = 1000;
    static final int MAX_HASH = 10;

    public IssuedDocument {
        require(providerDocumentId, MAX_TEXT);
        require(series, MAX_TEXT);
        require(fullNumber, MAX_TEXT);
        require(atcud, MAX_TEXT);
        require(qrPayload, MAX_QR);
        require(hashFragment, MAX_HASH);
        if (sequence < 1) {
            throw new ProviderRejectedException(INVALID_RESPONSE, "The provider gave an invalid sequence number");
        }
    }

    private static void require(String value, int max) {
        if (value == null || value.isEmpty() || value.length() > max) {
            throw new ProviderRejectedException(INVALID_RESPONSE, "The provider answered with a value of the wrong size");
        }
    }
}

package com.asp.platform.billing.domain;

import org.jspecify.annotations.Nullable;

/// What staff record about an instalment sale approved on the terminal: the financier, the number of monthly
/// instalments (2 to 6) and the terminal receipt number. ASP does not verify the approval.
public record Instalment(Financier financier, int count, @Nullable String reference) {

    public static final int MIN_COUNT = 2;
    public static final int MAX_COUNT = 6;
    public static final int MAX_REFERENCE_LENGTH = 60;

    /// @throws InvalidInstalmentException when the count is out of range or the reference is blank or too long
    public Instalment {
        if (count < MIN_COUNT || count > MAX_COUNT) {
            throw new InvalidInstalmentException("invalid-instalment-count", "instalment.count",
                    "Instalments must be between " + MIN_COUNT + " and " + MAX_COUNT);
        }
        if (reference != null && (reference.isBlank() || reference.length() > MAX_REFERENCE_LENGTH)) {
            throw new InvalidInstalmentException("invalid-instalment-reference", "instalment.reference",
                    "Reference must be 1 to " + MAX_REFERENCE_LENGTH + " characters");
        }
    }
}

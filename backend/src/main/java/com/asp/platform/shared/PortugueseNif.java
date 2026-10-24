package com.asp.platform.shared;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/// The Portuguese tax number (NIF / NIPC): 9 digits, a known first digit (or first two) and a mod-11 check digit.
/// Certified invoicing refuses documents with an invalid NIF, so ASP checks it when the country is Portugal.
public final class PortugueseNif {

    public static final String PORTUGAL = "PT";

    private static final Pattern NINE_DIGITS = Pattern.compile("\\d{9}");
    /// 1, 2, 3: people; 5: companies; 6: public bodies; 8: sole traders (old numbers).
    private static final Set<Character> FIRST_DIGITS = Set.of('1', '2', '3', '5', '6', '8');
    /// 45: non-resident people; 70-79: estates, condominiums, funds; 90-99: irregular entities and others.
    private static final Set<String> FIRST_TWO_DIGITS = Set.of("45", "70", "71", "72", "74", "75", "77", "79",
            "90", "91", "98", "99");

    private PortugueseNif() {
    }

    /// True for a valid NIF such as "123456789" or "509999999".
    public static boolean isValid(String nif) {
        if (!NINE_DIGITS.matcher(nif).matches()) {
            return false;
        }
        if (!FIRST_DIGITS.contains(nif.charAt(0)) && !FIRST_TWO_DIGITS.contains(nif.substring(0, 2))) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 8; i++) {
            sum += (nif.charAt(i) - '0') * (9 - i);
        }
        int remainder = sum % 11;
        int check = remainder < 2 ? 0 : 11 - remainder;
        return check == nif.charAt(8) - '0';
    }

    /// The rule applies only to Portugal: other countries' tax numbers are stored as entered.
    ///
    /// @param country ISO code of the country the number belongs to, in any case
    /// @return true when the NIF is absent, the country is not Portugal, or the NIF is valid
    public static boolean isAcceptable(@Nullable String nif, String country) {
        return nif == null || nif.isBlank() || !PORTUGAL.equals(country.trim().toUpperCase(Locale.ROOT))
                || isValid(nif.trim());
    }

    /// @throws InvalidNifException when [#isAcceptable] is false
    public static void check(@Nullable String nif, String country) {
        if (!isAcceptable(nif, country)) {
            throw new InvalidNifException();
        }
    }
}

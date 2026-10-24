package com.asp.platform.identity.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/// The short, unique name of a workshop in its branded login address `/w/{slug}`: 1 to 40 lower-case letters,
/// digits and single dashes, starting and ending with a letter or digit. Same rule as the V7 check constraint
/// (which does not forbid double dashes; this class does).
public record Slug(String value) {

    public static final int MAX_LENGTH = 40;

    private static final Pattern FORMAT = Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,38}[a-z0-9])?");
    private static final Pattern NOT_SLUG = Pattern.compile("[^a-z0-9]+");
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    /// Leaves room for a "-2", "-3", ... suffix when a slug made from a name is taken.
    private static final int BASE_LENGTH = 34;

    public Slug {
        if (!isValid(value)) {
            throw new IllegalArgumentException(
                    "Slug must be 1 to 40 lower-case letters, digits and single dashes, not starting or ending with a dash");
        }
    }

    public static boolean isValid(String value) {
        return FORMAT.matcher(value).matches() && !value.contains("--");
    }

    /// A slug made from a workshop name: accents folded, other characters turned into dashes.
    /// "Oficina São João, Lda." becomes "oficina-sao-joao-lda".
    public static Slug fromName(String name) {
        String folded = MARKS.matcher(Normalizer.normalize(name, Normalizer.Form.NFD)).replaceAll("");
        String slug = cut(trimDashes(NOT_SLUG.matcher(folded.toLowerCase(Locale.ROOT)).replaceAll("-")));
        return new Slug(slug.isEmpty() ? "workshop" : slug);
    }

    /// This slug with a number appended, for the n-th workshop whose name gives the same slug.
    public Slug numbered(int n) {
        return new Slug(cut(value) + "-" + n);
    }

    private static String cut(String slug) {
        return slug.length() > BASE_LENGTH ? trimDashes(slug.substring(0, BASE_LENGTH)) : slug;
    }

    private static String trimDashes(String s) {
        int start = 0;
        int end = s.length();
        while (start < end && s.charAt(start) == '-') {
            start++;
        }
        while (end > start && s.charAt(end - 1) == '-') {
            end--;
        }
        return s.substring(start, end);
    }
}

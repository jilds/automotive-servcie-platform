package com.asp.platform.identity.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/// A colour of a workshop's theme, as `#rrggbb` (stored lower case).
///
/// Contrast rule (WCAG 2.2 success criterion 1.4.3, level AA): text on a filled primary button must reach 4.5:1.
/// The workshop may pick any colour; [#textColor()] picks white text when white reaches 4.5:1 on it and black
/// otherwise. For every colour one of the two reaches at least 4.58:1, so no colour has to be rejected and no
/// button is ever unreadable.
public record BrandColor(String hex) {

    private static final Pattern HEX = Pattern.compile("#[0-9a-f]{6}");

    /// WCAG AA for normal-size text.
    public static final double MIN_TEXT_CONTRAST = 4.5;
    public static final BrandColor WHITE = new BrandColor("#ffffff");
    public static final BrandColor BLACK = new BrandColor("#000000");

    public BrandColor {
        hex = hex.trim().toLowerCase(Locale.ROOT);
        if (!HEX.matcher(hex).matches()) {
            throw new IllegalArgumentException("Colour must be #RRGGBB");
        }
    }

    /// WCAG relative luminance, 0 for black to 1 for white.
    public double luminance() {
        return 0.2126 * channel(1) + 0.7152 * channel(3) + 0.0722 * channel(5);
    }

    /// WCAG contrast ratio with another colour, from 1 to 21.
    public double contrastWith(BrandColor other) {
        double a = luminance();
        double b = other.luminance();
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    /// Text colour for a button filled with this colour: white when it reaches AA, otherwise black.
    public BrandColor textColor() {
        return contrastWith(WHITE) >= MIN_TEXT_CONTRAST ? WHITE : BLACK;
    }

    private double channel(int offset) {
        double c = Integer.parseInt(hex.substring(offset, offset + 2), 16) / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}

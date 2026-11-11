package com.asp.platform.reporting.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.jspecify.annotations.Nullable;

/// The CSV that Excel in Portugal opens: a UTF-8 byte order mark, `;` between cells, CRLF between lines, a decimal
/// comma. Text typed by users (names, plates) never becomes a formula: a cell that starts with `=`, `+`, `-`, `@`,
/// TAB or CR gets a leading `'`. Numbers are written by [#euros] and [#decimal], never from user text, so they are
/// not touched.
public final class Csv {

    private static final String BOM = String.valueOf((char) 0xFEFF);
    private static final String SEPARATOR = ";";
    private static final String LINE_END = "\r\n";
    private static final String FORMULA_STARTS = "=+-@\t\r";
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private Csv() {
    }

    /// @param header the column names
    /// @param rows   cells already written by the cell methods of this class
    public static String document(List<String> header, List<List<String>> rows) {
        StringBuilder csv = new StringBuilder(BOM);
        csv.append(String.join(SEPARATOR, header)).append(LINE_END);
        for (List<String> row : rows) {
            csv.append(String.join(SEPARATOR, row)).append(LINE_END);
        }
        return csv.toString();
    }

    /// A text cell: formula guard first, then quoting when it holds `;`, `"` or a line break.
    public static String text(@Nullable String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String safe = FORMULA_STARTS.indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
        boolean quote = safe.indexOf(';') >= 0 || safe.indexOf('"') >= 0 || safe.indexOf('\r') >= 0
                || safe.indexOf('\n') >= 0;
        return quote ? "\"" + safe.replace("\"", "\"\"") + "\"" : safe;
    }

    /// Cents as euros with two decimals and a decimal comma: 12345 becomes `123,45`.
    public static String euros(@Nullable Long cents) {
        return cents == null ? "" : BigDecimal.valueOf(cents, 2).toPlainString().replace('.', ',');
    }

    /// A number with two decimals and a decimal comma.
    public static String decimal(@Nullable BigDecimal value) {
        return value == null ? "" : value.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    public static String integer(@Nullable Long value) {
        return value == null ? "" : Long.toString(value);
    }

    /// `yyyy-MM-dd HH:mm` in the workshop's zone.
    public static String timestamp(@Nullable Instant value, ZoneId zone) {
        return value == null ? "" : TIMESTAMP.format(value.atZone(zone));
    }
}

package com.asp.platform.workshop.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.asp.platform.workshop.application.PublicQuoteView;
import com.asp.platform.workshop.domain.LineType;
import com.asp.platform.workshop.domain.Quote;
import com.asp.platform.workshop.domain.QuoteChannel;
import com.asp.platform.workshop.domain.QuoteLine;
import com.asp.platform.workshop.domain.QuoteStatus;

/// A quote on the customer's link: no customer data and no ids. Times are shown in `timeZone`.
///
/// @param vehicle   null when the vehicle is not found
/// @param canDecide status SENT and not past `validUntil`
record PublicQuoteResponse(int number, int workOrderNumber, QuoteStatus status, Instant sentAt, Instant validUntil,
        String timeZone, String workshopName, @Nullable Vehicle vehicle, List<Line> lines,
        List<QuoteResponse.Vat> vatByRate, long netCents, long vatCents, long totalCents, @Nullable Instant decidedAt,
        @Nullable QuoteChannel decidedChannel, boolean canDecide) {

    record Vehicle(String plateDisplay, @Nullable String plateEra, String registrationCountry, String label) {
    }

    record Line(LineType type, String description, BigDecimal quantity, long unitPriceCents,
            BigDecimal discountPercent, BigDecimal vatRate, long netCents) {

        static Line of(QuoteLine l) {
            return new Line(l.type(), l.description(), l.quantity(), l.unitPriceCents(), l.discountPercent(),
                    l.vatRate(), l.netCents());
        }
    }

    static PublicQuoteResponse of(PublicQuoteView view) {
        Quote q = view.quote();
        PublicQuoteView.Vehicle v = view.vehicle();
        return new PublicQuoteResponse(q.number(), view.workOrderNumber(), view.status(), q.sentAt(), q.validUntil(),
                view.timeZone().getId(), view.workshopName(),
                v == null ? null : new Vehicle(v.plateDisplay(), v.plateEra(), v.registrationCountry(), v.label()),
                q.lines().stream().map(Line::of).toList(), q.vatByRate().stream().map(QuoteResponse.Vat::of).toList(),
                q.netCents(), q.vatCents(), q.totalCents(), q.decidedAt(), q.decidedChannel(), view.canDecide());
    }
}

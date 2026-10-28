package com.asp.platform.workshop.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.asp.platform.workshop.application.QuoteView;
import com.asp.platform.workshop.domain.LineType;
import com.asp.platform.workshop.domain.Quote;
import com.asp.platform.workshop.domain.QuoteChannel;
import com.asp.platform.workshop.domain.QuoteLine;
import com.asp.platform.workshop.domain.QuoteStatus;
import com.asp.platform.workshop.domain.VatTotal;

/// A quote for the front desk and the owner (mechanics never get one).
///
/// @param status        EXPIRED for a SENT quote past `validUntil`
/// @param decidedName   the name the customer typed when approving on the link
/// @param decidedByName the staff member who recorded the decision for the customer
/// @param decidedNote   the staff note, or the customer's rejection reason
record QuoteResponse(UUID id, int number, UUID workOrderId, QuoteStatus status, Instant sentAt,
        @Nullable String sentByName, Instant validUntil, long netCents, long vatCents, long totalCents,
        List<Line> lines, List<Vat> vatByRate, @Nullable Instant decidedAt, @Nullable QuoteChannel decidedChannel,
        @Nullable String decidedName, @Nullable String decidedByName, @Nullable String decidedNote, long version) {

    record Line(int position, LineType type, String description, BigDecimal quantity, long unitPriceCents,
            BigDecimal discountPercent, BigDecimal vatRate, long netCents, long vatCents,
            @Nullable UUID serviceItemId, @Nullable UUID productId) {

        static Line of(QuoteLine l) {
            return new Line(l.position(), l.type(), l.description(), l.quantity(), l.unitPriceCents(),
                    l.discountPercent(), l.vatRate(), l.netCents(), l.vatCents(), l.serviceItemId(), l.productId());
        }
    }

    record Vat(BigDecimal vatRate, long netCents, long vatCents) {

        static Vat of(VatTotal v) {
            return new Vat(v.vatRate(), v.netCents(), v.vatCents());
        }
    }

    static QuoteResponse of(QuoteView view) {
        Quote q = view.quote();
        return new QuoteResponse(q.id(), q.number(), q.workOrderId(), view.status(), q.sentAt(), view.sentByName(),
                q.validUntil(), q.netCents(), q.vatCents(), q.totalCents(),
                q.lines().stream().map(Line::of).toList(), q.vatByRate().stream().map(Vat::of).toList(),
                q.decidedAt(), q.decidedChannel(), q.decidedName(), view.decidedByName(), q.decidedNote(),
                q.version());
    }
}

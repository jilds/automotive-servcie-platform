package com.asp.platform.billing.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/// Documents and payments for the pure domain tests.
final class Fixtures {

    static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");
    static final UUID USER = UUID.randomUUID();

    private Fixtures() {
    }

    /// One labour line: net 813 + VAT 187 = 1000 cents.
    static InvoiceLine line() {
        return new InvoiceLine(UUID.randomUUID(), 1, LineKind.LABOR, "Mudança de óleo", BigDecimal.ONE, 813,
                BigDecimal.ZERO, new BigDecimal("23.00"), 813, 187, null);
    }

    static Invoice pending() {
        return Invoice.open(UUID.randomUUID(), DocumentKind.INVOICE, UUID.randomUUID(), 7, null, UUID.randomUUID(),
                "AA-00-AA", new IssuerSnapshot("Oficina", "509999999", "PT"),
                new CustomerSnapshot("Ana", "123456789", false, null), List.of(line()), null, "sandbox", USER, NOW);
    }

    static IssuedDocument issuedDocument(long totalCents) {
        return new IssuedDocument("doc-1", "SANDBOX", 1, "FT SANDBOX/1", "SANDBOX-1", "qr", "SBOX", NOW, totalCents);
    }

    static Invoice issued() {
        Invoice invoice = pending();
        invoice.markIssued(issuedDocument(1000), NOW);
        return invoice;
    }

    static Payment payment(PaymentStatus status, long amountCents) {
        boolean viaProvider = status != PaymentStatus.PAID;
        return new Payment(UUID.randomUUID(), UUID.randomUUID(),
                viaProvider ? PaymentMethod.MULTIBANCO : PaymentMethod.CASH, status, amountCents,
                viaProvider ? "sandbox" : null, viaProvider ? "sbx-1" : null, viaProvider ? "99999" : null,
                viaProvider ? "123456789" : null, null, viaProvider ? NOW.plusSeconds(3600) : null,
                status == PaymentStatus.PAID ? NOW : null, null, null, USER, NOW, 0);
    }
}

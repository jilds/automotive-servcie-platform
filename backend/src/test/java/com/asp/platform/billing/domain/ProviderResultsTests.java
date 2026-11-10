package com.asp.platform.billing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/// What a provider hands back is bounded before it reaches the database: an over-long value is a refusal of the
/// provider's answer (the document becomes FAILED), not a database error.
class ProviderResultsTests {

    private static IssuedDocument document(String id, String series, String full, String atcud, String qr,
            String hash) {
        return new IssuedDocument(id, series, 1, full, atcud, qr, hash, Instant.EPOCH, 1000);
    }

    @Test
    void should_accept_values_at_the_limits() {
        assertThatCode(() -> document("d".repeat(100), "s".repeat(100), "f".repeat(100), "a".repeat(100),
                "q".repeat(1000), "h".repeat(10))).doesNotThrowAnyException();
    }

    @Test
    void should_refuse_a_document_with_an_over_long_value() {
        for (IssuedDocumentCase c : new IssuedDocumentCase[] {
                new IssuedDocumentCase("id", () -> document("d".repeat(101), "s", "f", "a", "q", "h")),
                new IssuedDocumentCase("series", () -> document("d", "s".repeat(101), "f", "a", "q", "h")),
                new IssuedDocumentCase("full", () -> document("d", "s", "f".repeat(101), "a", "q", "h")),
                new IssuedDocumentCase("atcud", () -> document("d", "s", "f", "a".repeat(101), "q", "h")),
                new IssuedDocumentCase("qr", () -> document("d", "s", "f", "a", "q".repeat(1001), "h")),
                new IssuedDocumentCase("hash", () -> document("d", "s", "f", "a", "q", "h".repeat(11)))}) {
            assertThatThrownBy(c.create()::run).as(c.name()).isInstanceOfSatisfying(ProviderRejectedException.class,
                    e -> assertThat(e.code()).isEqualTo("provider-invalid-response"));
        }
    }

    @Test
    void should_refuse_a_payment_reference_with_an_over_long_value() {
        assertThatCode(() -> new PaymentProvider.Reference("p".repeat(100), "99999", "123456789"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> new PaymentProvider.Reference("p".repeat(101), null, null))
                .isInstanceOf(ProviderRejectedException.class);
        assertThatThrownBy(() -> new PaymentProvider.Reference("p", "1234567", "123456789"))
                .isInstanceOf(ProviderRejectedException.class);
        assertThatThrownBy(() -> new PaymentProvider.Reference("p", "99999", "12345"))
                .isInstanceOf(ProviderRejectedException.class);
    }

    private record IssuedDocumentCase(String name, java.util.function.Supplier<IssuedDocument> create0) {

        Runnable create() {
            return create0::get;
        }
    }
}

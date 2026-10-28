package com.asp.platform.workshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.asp.platform.workshop.application.QuoteExpiry;

/// The hourly quote expiry over every open workshop, run directly (scheduling is off in the tests): SENT quotes past
/// their validity are stored as EXPIRED in each workshop; quotes still valid or already decided stay; the work orders
/// do not move.
class QuoteExpiryTests extends QuoteTestSupport {

    @Autowired
    QuoteExpiry expiry;

    @Test
    void should_store_quotes_past_their_validity_as_expired_in_every_workshop() throws Exception {
        Shop a = garage("Expira Orcamento A");
        Shop b = garage("Expira Orcamento B");
        // Workshop B keeps its quotes open for 30 days.
        call(put("/api/v1/workshop/settings").content(withVersion("""
                {"name":"Expira Orcamento B","country":"PT","timezone":"Europe/Lisbon","defaultVatRate":23,
                 "labourRateCents":0,"quoteValidDays":30}""", versionAt(b.owner(), "/api/v1/workshop/settings"))),
                b.owner()).andExpect(status().isOk());

        String woA = quotableOrder(a, false);
        String dueA = idOfQuote(sendQuote(a, woA));
        String decidedWo = quotableOrder(a, false);
        String decided = idOfQuote(sendQuote(a, decidedWo));
        decide(a.owner(), decidedWo, decided, "{\"decision\":\"REJECTED\",\"note\":\"Não\",\"version\":0}")
                .andExpect(status().isOk());
        String woB = quotableOrder(b, false);
        String validB = idOfQuote(sendQuote(b, woB));
        String woB2 = quotableOrder(b, false);
        String dueB = idOfQuote(sendQuote(b, woB2));
        pastValidity(b, dueB);

        // Nothing of A is due yet; B's backdated quote is.
        assertThat(expiry.expireDue(Instant.now())).isGreaterThanOrEqualTo(1);
        assertThat(storedStatus(b, dueB)).isEqualTo("EXPIRED");
        assertThat(storedStatus(a, dueA)).isEqualTo("SENT");

        assertThat(expiry.expireDue(Instant.now().plus(Duration.ofDays(16)))).isGreaterThanOrEqualTo(1);

        assertThat(storedStatus(a, dueA)).isEqualTo("EXPIRED");
        assertThat(storedStatus(a, decided)).isEqualTo("REJECTED");
        assertThat(storedStatus(b, validB)).isEqualTo("SENT");
        assertThat(workOrderStatus(a, woA)).isEqualTo("AWAITING_APPROVAL");
        assertThat(workOrderStatus(b, woB2)).isEqualTo("AWAITING_APPROVAL");

        // Run again: nothing of these workshops is left to expire.
        expiry.expireDue(Instant.now().plus(Duration.ofDays(16)));
        assertThat(storedStatus(b, validB)).isEqualTo("SENT");
        assertThat(quoteStatuses(a, woA)).containsExactly("EXPIRED");
    }
}

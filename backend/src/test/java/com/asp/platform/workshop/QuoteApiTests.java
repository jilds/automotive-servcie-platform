package com.asp.platform.workshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import com.jayway.jsonpath.JsonPath;

/// Quotes over the staff API: sending (snapshot, validity, order move, mail event), superseding on line changes,
/// a new send and cancel, staff decisions with the order moves, versions, expiry as read and as decided, who may do
/// what (mechanics: status only), isolation between workshops and the `quoteValidDays` setting.
@RecordApplicationEvents
class QuoteApiTests extends QuoteTestSupport {

    @Test
    void should_send_a_frozen_copy_of_the_lines_and_move_the_order_to_awaiting_approval(ApplicationEvents events)
            throws Exception {
        Shop g = garage("Orcamento Envio");
        String wo = quotableOrder(g, true);
        labour(g, wo, "Lavagem", 1000, 6);

        String body = send(g.owner(), wo)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerHasEmail").value(true))
                .andExpect(jsonPath("$.quote.number").value(1))
                .andExpect(jsonPath("$.quote.workOrderId").value(wo))
                .andExpect(jsonPath("$.quote.status").value("SENT"))
                .andExpect(jsonPath("$.quote.sentByName").value("Owner"))
                .andExpect(jsonPath("$.quote.lines.length()").value(3))
                .andExpect(jsonPath("$.quote.lines[0].position").value(1))
                .andExpect(jsonPath("$.quote.lines[0].type").value("LABOR"))
                .andExpect(jsonPath("$.quote.lines[0].description").value("Mudança de óleo"))
                .andExpect(jsonPath("$.quote.lines[0].unitPriceCents").value(4000))
                .andExpect(jsonPath("$.quote.lines[0].netCents").value(4000))
                .andExpect(jsonPath("$.quote.lines[0].vatCents").value(920))
                .andExpect(jsonPath("$.quote.lines[1].type").value("PART"))
                .andExpect(jsonPath("$.quote.lines[1].discountPercent").value(10))
                .andExpect(jsonPath("$.quote.lines[1].netCents").value(2250))
                .andExpect(jsonPath("$.quote.lines[1].vatCents").value(518))
                .andExpect(jsonPath("$.quote.lines[1].productId").isEmpty())
                .andExpect(jsonPath("$.quote.lines[2].position").value(3))
                .andExpect(jsonPath("$.quote.netCents").value(7250))
                .andExpect(jsonPath("$.quote.vatCents").value(1498))
                .andExpect(jsonPath("$.quote.totalCents").value(8748))
                .andExpect(jsonPath("$.quote.vatByRate.length()").value(2))
                .andExpect(jsonPath("$.quote.vatByRate[0].vatRate").value(6))
                .andExpect(jsonPath("$.quote.vatByRate[0].netCents").value(1000))
                .andExpect(jsonPath("$.quote.vatByRate[0].vatCents").value(60))
                .andExpect(jsonPath("$.quote.vatByRate[1].vatRate").value(23))
                .andExpect(jsonPath("$.quote.vatByRate[1].netCents").value(6250))
                .andExpect(jsonPath("$.quote.vatByRate[1].vatCents").value(1438))
                .andExpect(jsonPath("$.quote.decidedAt").isEmpty())
                .andExpect(jsonPath("$.quote.version").value(0))
                .andReturn().getResponse().getContentAsString();
        String token = tokenOfQuote(body);
        assertThat(token).hasSize(43);
        Instant sentAt = Instant.parse(JsonPath.read(body, "$.quote.sentAt"));
        Instant validUntil = Instant.parse(JsonPath.read(body, "$.quote.validUntil"));
        assertThat(Duration.between(sentAt, validUntil)).isEqualTo(Duration.ofDays(15));

        call(get("/api/v1/work-orders/" + wo), g.owner())
                .andExpect(jsonPath("$.status").value("AWAITING_APPROVAL"))
                .andExpect(jsonPath("$.quote.id").value(idOfQuote(body)))
                .andExpect(jsonPath("$.quote.number").value(1))
                .andExpect(jsonPath("$.quote.status").value("SENT"))
                .andExpect(jsonPath("$.quote.validUntil").exists());
        call(get("/api/v1/work-orders/board"), g.owner())
                .andExpect(jsonPath("$.columns.AWAITING_APPROVAL[0].quoteStatus").value("SENT"));

        List<com.asp.platform.workshop.QuoteSent> sent = events.stream(com.asp.platform.workshop.QuoteSent.class)
                .toList();
        assertThat(sent).hasSize(1);
        assertThat(sent.getFirst().statusToken()).isEqualTo(token);
        assertThat(sent.getFirst().email()).isEqualTo("ana@cliente.pt");
        assertThat(sent.getFirst().plate()).isEqualTo(PLATE);
        assertThat(sent.getFirst().totalCents()).isEqualTo(8748);
        assertThat(sent.getFirst().toString()).doesNotContain(token, "ana@cliente.pt");
        // Only the hash of the token is stored.
        assertThat(this.<Integer>asWorkshop(g, connection -> {
            try (PreparedStatement select = connection.prepareStatement(
                    "select count(*) from quote where status_token_hash = ?")) {
                select.setString(1, token);
                var rs = select.executeQuery();
                rs.next();
                return rs.getInt(1);
            }
        })).isZero();
    }

    @Test
    void should_keep_the_snapshot_when_the_lines_change_and_supersede_the_quote() throws Exception {
        Shop g = garage("Orcamento Snapshot");
        String wo = quotableOrder(g, false);
        String quoteId = idOfQuote(sendQuote(g, wo));

        call(post("/api/v1/work-orders/" + wo + "/lines").content("""
                {"type":"LABOR","description":"Extra","quantity":1,"unitPriceCents":999,"vatRate":23}"""), g.owner())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quote.status").value("SUPERSEDED"));

        call(get("/api/v1/work-orders/" + wo + "/quotes"), g.owner())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(quoteId))
                .andExpect(jsonPath("$[0].status").value("SUPERSEDED"))
                .andExpect(jsonPath("$[0].lines.length()").value(1))
                .andExpect(jsonPath("$[0].totalCents").value(4920));

        // The app role may not change or remove a snapshot line.
        assertThatThrownBy(() -> asWorkshop(g, connection -> {
            try (PreparedStatement update = connection.prepareStatement(
                    "update quote_line set unit_price_cents = 1")) {
                return update.executeUpdate();
            }
        })).isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("permission denied");
        assertThatThrownBy(() -> asWorkshop(g, connection -> {
            try (PreparedStatement remove = connection.prepareStatement("delete from quote_line")) {
                return remove.executeUpdate();
            }
        })).isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("permission denied");
        assertThatThrownBy(() -> asWorkshop(g, connection -> {
            try (PreparedStatement remove = connection.prepareStatement("delete from quote")) {
                return remove.executeUpdate();
            }
        })).isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("permission denied");
        // Nor the frozen columns of the quote: only its status and decision may change.
        for (String frozen : new String[] {"net_cents = 0, vat_cents = 0, total_cents = 0",
                "valid_until = now() + interval '1 year'", "status_token_hash = repeat('0', 64)",
                "sent_at = now()", "work_order_id = work_order_id", "number = number + 100"}) {
            assertThatThrownBy(() -> asWorkshop(g, connection -> {
                try (PreparedStatement update = connection.prepareStatement("update quote set " + frozen)) {
                    return update.executeUpdate();
                }
            })).as(frozen).isInstanceOf(DataAccessException.class).rootCause()
                    .hasMessageContaining("permission denied");
        }
        assertThat(this.<Integer>asWorkshop(g, connection -> {
            try (PreparedStatement update = connection.prepareStatement(
                    "update quote set updated_at = updated_at, version = version where id = ?::uuid")) {
                update.setString(1, quoteId);
                return update.executeUpdate();
            }
        })).isEqualTo(1);
    }

    @Test
    void should_supersede_on_line_removal_on_a_new_send_and_on_cancel_but_not_on_check_in_edits() throws Exception {
        Shop g = garage("Orcamento Substitui");
        String wo = quotableOrder(g, true);
        sendQuote(g, wo);

        call(put("/api/v1/work-orders/" + wo + "/check-in").content("""
                {"mileageIn":100100,"fuelLevel":5,"complaint":"Barulho ao travar"}"""), g.owner())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quote.status").value("SENT"));

        String lineId = JsonPath.read(call(get("/api/v1/work-orders/" + wo), g.owner()).andReturn().getResponse()
                .getContentAsString(), "$.lines[1].id");
        call(delete("/api/v1/work-orders/" + wo + "/lines/" + lineId), g.owner())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quote.status").value("SUPERSEDED"));

        String second = sendQuote(g, wo);
        assertThat(JsonPath.<Integer>read(second, "$.quote.number")).isEqualTo(2);
        String third = sendQuote(g, wo);
        assertThat(quoteStatuses(g, wo)).containsExactly("SENT", "SUPERSEDED", "SUPERSEDED");
        call(get("/api/v1/work-orders/" + wo + "/quotes"), g.owner())
                .andExpect(jsonPath("$[0].id").value(idOfQuote(third)))
                .andExpect(jsonPath("$[1].id").value(idOfQuote(second)))
                .andExpect(jsonPath("$[0].lines.length()").value(1));

        call(patch("/api/v1/work-orders/" + wo + "/status").content("{\"status\":\"CANCELLED\"}"), g.owner())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quote.status").value("SUPERSEDED"));
        assertThat(quoteStatuses(g, wo)).containsExactly("SUPERSEDED", "SUPERSEDED", "SUPERSEDED");
    }

    @Test
    void should_supersede_when_lines_come_from_stock() throws Exception {
        Shop g = garage("Orcamento Stock");
        String filter = product(g, "FLT-01", "Filtro", 1500, false, 5);
        String wo = quotableOrder(g, false);
        sendQuote(g, wo);

        call(post("/api/v1/work-orders/" + wo + "/lines/from-stock")
                .content("{\"productId\":\"%s\",\"quantity\":1}".formatted(filter)), g.owner())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quote.status").value("SUPERSEDED"));
    }

    @Test
    void should_refuse_to_send_without_lines_or_in_the_wrong_status() throws Exception {
        Shop g = garage("Orcamento Recusa");
        String empty = workOrder(g, vehicle(g, true), null);
        send(g.owner(), empty)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("work-order-not-quotable"));

        labour(g, empty, "Revisão", 5000, 23);
        call(patch("/api/v1/work-orders/" + empty + "/status").content("{\"status\":\"IN_PROGRESS\"}"), g.owner())
                .andExpect(status().isOk());
        send(g.owner(), empty)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("work-order-not-quotable"));

        send(g.owner(), "00000000-0000-0000-0000-000000000001")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("work-order-not-found"));
    }

    @Test
    void should_record_a_staff_approval_with_a_note_and_move_the_order_to_in_progress() throws Exception {
        Shop g = garage("Orcamento Telefone");
        String desk = member(g, "FRONT_DESK");
        String wo = quotableOrder(g, false);
        String quoteId = idOfQuote(sendQuote(g, wo));

        for (String note : new String[] {"", "   ", null}) {
            String n = note == null ? "null" : "\"" + note + "\"";
            decide(desk, wo, quoteId, "{\"decision\":\"APPROVED\",\"note\":%s,\"version\":0}".formatted(n))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("decision-note-required"))
                    .andExpect(jsonPath("$.errors.note").exists());
        }
        decide(desk, wo, quoteId, "{\"decision\":\"MAYBE\",\"note\":\"x\",\"version\":0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation"))
                .andExpect(jsonPath("$.errors.decision").exists());
        decide(desk, wo, quoteId, "{\"decision\":\"APPROVED\",\"note\":\"%s\",\"version\":0}".formatted("x".repeat(501)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.note").exists());
        decide(desk, wo, quoteId, "{\"decision\":\"APPROVED\",\"note\":\"Por telefone\",\"version\":3}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("stale-data"));

        decide(desk, wo, quoteId, "{\"decision\":\"APPROVED\",\"note\":\"Por telefone às 10h\",\"version\":0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decidedChannel").value("STAFF"))
                .andExpect(jsonPath("$.decidedByName").value("front_desk"))
                .andExpect(jsonPath("$.decidedNote").value("Por telefone às 10h"))
                .andExpect(jsonPath("$.decidedName").isEmpty())
                .andExpect(jsonPath("$.decidedAt").exists())
                .andExpect(jsonPath("$.version").value(1));
        assertThat(workOrderStatus(g, wo)).isEqualTo("IN_PROGRESS");
        call(get("/api/v1/work-orders/" + wo), g.owner())
                .andExpect(jsonPath("$.quote.status").value("APPROVED"))
                .andExpect(jsonPath("$.quote.decidedChannel").value("STAFF"));

        decide(desk, wo, quoteId, "{\"decision\":\"REJECTED\",\"note\":\"Mudou de ideias\",\"version\":1}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("quote-not-open"));
        // A line change after the approval supersedes nothing.
        labour(g, wo, "Extra", 1000, 23).andExpect(jsonPath("$.quote.status").value("APPROVED"));
    }

    @Test
    void should_move_to_awaiting_parts_when_the_approved_quote_has_a_part_typed_by_hand() throws Exception {
        Shop g = garage("Orcamento Pecas");
        String wo = quotableOrder(g, true);
        String quoteId = idOfQuote(sendQuote(g, wo));

        decide(g.owner(), wo, quoteId, "{\"decision\":\"APPROVED\",\"note\":\"Ao balcão\",\"version\":0}")
                .andExpect(status().isOk());

        assertThat(workOrderStatus(g, wo)).isEqualTo("AWAITING_PARTS");
    }

    @Test
    void should_go_to_in_progress_when_the_only_part_comes_from_stock() throws Exception {
        Shop g = garage("Orcamento Pecas Stock");
        String filter = product(g, "FLT-01", "Filtro", 1500, false, 5);
        String wo = quotableOrder(g, false);
        call(post("/api/v1/work-orders/" + wo + "/lines/from-stock")
                .content("{\"productId\":\"%s\",\"quantity\":1}".formatted(filter)), g.owner())
                .andExpect(status().isCreated());
        String quoteId = idOfQuote(sendQuote(g, wo));

        decide(g.owner(), wo, quoteId, "{\"decision\":\"APPROVED\",\"note\":\"Ao balcão\",\"version\":0}")
                .andExpect(status().isOk());

        assertThat(workOrderStatus(g, wo)).isEqualTo("IN_PROGRESS");
    }

    @Test
    void should_record_a_rejection_without_moving_the_order_and_only_the_decision_when_moved_by_hand()
            throws Exception {
        Shop g = garage("Orcamento Rejeita");
        String wo = quotableOrder(g, false);
        String quoteId = idOfQuote(sendQuote(g, wo));
        decide(g.owner(), wo, quoteId, "{\"decision\":\"REJECTED\",\"note\":\"Caro demais\",\"version\":0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.decidedNote").value("Caro demais"));
        assertThat(workOrderStatus(g, wo)).isEqualTo("AWAITING_APPROVAL");
        call(get("/api/v1/work-orders/board"), g.owner())
                .andExpect(jsonPath("$.columns.AWAITING_APPROVAL[0].quoteStatus").value("REJECTED"));

        String other = quotableOrder(g, true);
        String otherQuote = idOfQuote(sendQuote(g, other));
        call(patch("/api/v1/work-orders/" + other + "/status").content("{\"status\":\"IN_PROGRESS\"}"), g.owner())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quote.status").value("SENT"));
        decide(g.owner(), other, otherQuote, "{\"decision\":\"APPROVED\",\"note\":\"Ok\",\"version\":0}")
                .andExpect(status().isOk());
        assertThat(workOrderStatus(g, other)).isEqualTo("IN_PROGRESS");
    }

    @Test
    void should_read_and_store_an_expired_quote_and_refuse_a_decision() throws Exception {
        Shop g = garage("Orcamento Expira");
        String wo = quotableOrder(g, false);
        String quoteId = idOfQuote(sendQuote(g, wo));
        pastValidity(g, quoteId);

        call(get("/api/v1/work-orders/" + wo + "/quotes"), g.owner()).andExpect(jsonPath("$[0].status").value("EXPIRED"));
        call(get("/api/v1/work-orders/" + wo), g.owner()).andExpect(jsonPath("$.quote.status").value("EXPIRED"));
        call(get("/api/v1/work-orders/board"), g.owner())
                .andExpect(jsonPath("$.columns.AWAITING_APPROVAL[0].quoteStatus").value("EXPIRED"));
        assertThat(storedStatus(g, quoteId)).isEqualTo("SENT");

        decide(g.owner(), wo, quoteId, "{\"decision\":\"APPROVED\",\"note\":\"Tarde\",\"version\":0}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("quote-expired"));
        assertThat(storedStatus(g, quoteId)).isEqualTo("EXPIRED");
        assertThat(workOrderStatus(g, wo)).isEqualTo("AWAITING_APPROVAL");
        decide(g.owner(), wo, quoteId, "{\"decision\":\"APPROVED\",\"note\":\"Tarde\",\"version\":1}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("quote-not-open"));

        // A new send works and is open again.
        sendQuote(g, wo);
        assertThat(quoteStatuses(g, wo)).containsExactly("SENT", "EXPIRED");
    }

    @Test
    void should_keep_mechanics_out_of_quotes_and_show_them_the_status_only() throws Exception {
        Shop g = garage("Orcamento Mecanico");
        String mechanicEmail = uniqueEmail("mec");
        String mechanicId = invite(g.owner(), "MECHANIC", mechanicEmail);
        String mechanic = login(mechanicEmail, PASSWORD);
        String wo = workOrder(g, vehicle(g, true), mechanicId);
        labour(g, wo, "Revisão", 5000, 23);
        String quoteId = idOfQuote(sendQuote(g, wo));

        send(mechanic, wo).andExpect(status().isForbidden());
        call(get("/api/v1/work-orders/" + wo + "/quotes"), mechanic).andExpect(status().isForbidden());
        decide(mechanic, wo, quoteId, "{\"decision\":\"APPROVED\",\"note\":\"Eu\",\"version\":0}")
                .andExpect(status().isForbidden());

        String seen = call(get("/api/v1/work-orders/" + wo), mechanic)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quote.status").value("SENT"))
                .andExpect(jsonPath("$.quote.number").value(1))
                .andExpect(jsonPath("$.totalCents").isEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat((java.util.Map<String, Object>) JsonPath.read(seen, "$.quote"))
                .containsOnlyKeys("id", "number", "status", "validUntil", "decidedAt", "decidedChannel");
        call(get("/api/v1/work-orders/board"), mechanic)
                .andExpect(jsonPath("$.columns.AWAITING_APPROVAL[0].quoteStatus").value("SENT"));
    }

    @Test
    void should_keep_quotes_inside_their_workshop() throws Exception {
        Shop a = garage("Orcamento Isola A");
        Shop b = garage("Orcamento Isola B");
        String wo = quotableOrder(a, false);
        String sent = sendQuote(a, wo);
        String quoteId = idOfQuote(sent);
        String otherWo = quotableOrder(a, false);

        call(get("/api/v1/work-orders/" + wo + "/quotes"), b.owner())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("work-order-not-found"));
        send(b.owner(), wo).andExpect(status().isNotFound());
        decide(b.owner(), wo, quoteId, "{\"decision\":\"APPROVED\",\"note\":\"Hack\",\"version\":0}")
                .andExpect(status().isNotFound());
        // A quote of another order of the same workshop.
        decide(a.owner(), otherWo, quoteId, "{\"decision\":\"APPROVED\",\"note\":\"Hack\",\"version\":0}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("quote-not-found"));
        assertThat(storedStatus(b, quoteId)).isEqualTo("missing");
        assertThat(storedStatus(a, quoteId)).isEqualTo("SENT");
        // Workshop B's quote numbers start at 1 too.
        assertThat(JsonPath.<Integer>read(sendQuote(b, quotableOrder(b, false)), "$.quote.number")).isEqualTo(1);
    }

    @Test
    void should_use_the_workshops_quote_validity_and_say_when_the_customer_has_no_email(ApplicationEvents events)
            throws Exception {
        Shop g = garage("Orcamento Validade");
        call(get("/api/v1/workshop/settings"), g.owner()).andExpect(jsonPath("$.quoteValidDays").value(15));
        for (String days : new String[] {"0", "91"}) {
            call(put("/api/v1/workshop/settings").content(withVersion("""
                    {"name":"Oficina","country":"PT","timezone":"Europe/Lisbon","defaultVatRate":23,
                     "labourRateCents":0,"quoteValidDays":%s}""".formatted(days),
                    versionAt(g.owner(), "/api/v1/workshop/settings"))), g.owner())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("validation"))
                    .andExpect(jsonPath("$.errors.quoteValidDays").exists());
        }
        call(put("/api/v1/workshop/settings").content(withVersion("""
                {"name":"Oficina","country":"PT","timezone":"Europe/Lisbon","defaultVatRate":23,
                 "labourRateCents":0,"quoteValidDays":30}""", versionAt(g.owner(), "/api/v1/workshop/settings"))),
                g.owner())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quoteValidDays").value(30));
        // Left out (or null), the stored validity stays.
        for (String days : new String[] {"", ",\"quoteValidDays\":null"}) {
            call(put("/api/v1/workshop/settings").content(withVersion("""
                    {"name":"Oficina","country":"PT","timezone":"Europe/Lisbon","defaultVatRate":23,
                     "labourRateCents":0%s}""".formatted(days), versionAt(g.owner(), "/api/v1/workshop/settings"))),
                    g.owner())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.quoteValidDays").value(30));
        }

        String wo = workOrder(g, vehicle(g, false), null);
        labour(g, wo, "Revisão", 5000, 23);
        String body = send(g.owner(), wo)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerHasEmail").value(false))
                .andReturn().getResponse().getContentAsString();
        assertThat(Duration.between(Instant.parse(JsonPath.read(body, "$.quote.sentAt")),
                Instant.parse(JsonPath.read(body, "$.quote.validUntil")))).isEqualTo(Duration.ofDays(30));
        assertThat(events.stream(com.asp.platform.workshop.QuoteSent.class)).isEmpty();
    }
}

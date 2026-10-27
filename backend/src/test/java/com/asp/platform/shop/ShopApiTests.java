package com.asp.platform.shop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

/// The front desk's shop orders over the API: preparing, the hand-over with the stock issue, cancelling with a
/// reason, versions, the settings, who may do what and isolation between workshops.
class ShopApiTests extends ShopTestSupport {

    @Test
    void should_prepare_and_hand_over_an_order_issuing_its_stock() throws Exception {
        Shop s = shop("Loja Balcao", true);
        String desk = member(s, "FRONT_DESK");
        String filter = product(s, "FLT-OL-01", "Filtro de óleo", 1200, true, 5);
        placeAndGetToken(s, filter, 2);
        String id = orderId(s, 1);

        String placed = call(get("/api/v1/shop-orders/" + id), desk)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PLACED"))
                .andExpect(jsonPath("$.name").value("Ana Silva"))
                .andExpect(jsonPath("$.phone").value("912345678"))
                .andExpect(jsonPath("$.email").value("ana@example.pt"))
                .andExpect(jsonPath("$.nif").value("123456789"))
                .andExpect(jsonPath("$.marketingConsent").value(false))
                .andExpect(jsonPath("$.lines[0].productId").value(filter))
                .andExpect(jsonPath("$.lines[0].sku").value("FLT-OL-01"))
                .andExpect(jsonPath("$.lines[0].unitPriceCents").value(1200))
                .andExpect(jsonPath("$.lines[0].vatRate").value(23))
                .andExpect(jsonPath("$.lines[0].unitPriceWithVatCents").value(1476))
                .andExpect(jsonPath("$.lines[0].lineTotalCents").value(2952))
                .andExpect(jsonPath("$.totalCents").value(2952))
                .andExpect(jsonPath("$.readyAt").doesNotExist())
                .andExpect(jsonPath("$.paymentMethod").doesNotExist())
                .andExpect(jsonPath("$.cancelledBy").doesNotExist())
                .andExpect(jsonPath("$.version").value(0))
                .andReturn().getResponse().getContentAsString();
        Instant placedHold = Instant.parse(JsonPath.read(placed, "$.holdUntil"));
        Instant placedAt = Instant.parse(JsonPath.read(placed, "$.placedAt"));
        assertThat(Duration.between(placedAt, placedHold)).isEqualTo(Duration.ofDays(3));
        call(get("/api/v1/shop-orders/pending-count"), desk).andExpect(jsonPath("$.count").value(1));

        String ready = call(post("/api/v1/shop-orders/" + id + "/ready").content("{\"version\":0}"), desk)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.readyAt").exists())
                .andExpect(jsonPath("$.version").value(1))
                .andReturn().getResponse().getContentAsString();
        assertThat(Instant.parse(JsonPath.read(ready, "$.holdUntil"))).isAfter(placedHold);
        call(get("/api/v1/shop-orders/pending-count"), desk).andExpect(jsonPath("$.count").value(0));
        call(post("/api/v1/shop-orders/" + id + "/ready").content("{\"version\":1}"), desk)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("shop-order-not-open"));

        call(post("/api/v1/shop-orders/" + id + "/collect").content("{\"paymentMethod\":\"BITCOIN\",\"version\":1}"),
                desk)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid-payment-method"))
                .andExpect(jsonPath("$.errors.paymentMethod").exists());
        call(post("/api/v1/shop-orders/" + id + "/collect").content("{\"version\":1}"), desk)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid-payment-method"));
        call(post("/api/v1/shop-orders/" + id + "/collect").content("{\"paymentMethod\":\"CARD\",\"version\":1}"),
                desk)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COLLECTED"))
                .andExpect(jsonPath("$.paymentMethod").value("CARD"))
                .andExpect(jsonPath("$.collectedByName").value("front_desk"))
                .andExpect(jsonPath("$.collectedAt").exists())
                .andExpect(jsonPath("$.version").value(2));

        assertThat(onHand(s, filter)).isEqualTo(3);
        assertThat(available(s, filter)).isEqualTo(3);
        call(get("/api/v1/stock/products/" + filter + "/movements"), s.owner())
                .andExpect(jsonPath("$[0].type").value("ISSUE"))
                .andExpect(jsonPath("$[0].quantity").value(-2))
                .andExpect(jsonPath("$[0].referenceType").value("SHOP_ORDER"))
                .andExpect(jsonPath("$[0].referenceId").value(id));
        for (String move : new String[] {"ready", "collect", "cancel"}) {
            call(post("/api/v1/shop-orders/" + id + "/" + move)
                    .content("{\"paymentMethod\":\"CASH\",\"reason\":\"Desistiu\",\"version\":2}"), desk)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("shop-order-not-open"));
        }
    }

    @Test
    void should_collect_a_placed_order_directly() throws Exception {
        Shop s = shop("Loja Direta", true);
        String filter = product(s, "FLT-OL-01", "Filtro de óleo", 1200, true, 5);
        placeAndGetToken(s, filter, 1);

        call(post("/api/v1/shop-orders/" + orderId(s, 1) + "/collect")
                .content("{\"paymentMethod\":\"MBWAY\",\"version\":0}"), s.owner())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COLLECTED"))
                .andExpect(jsonPath("$.collectedByName").value("Owner"));
        assertThat(onHand(s, filter)).isEqualTo(4);
    }

    @Test
    void should_cancel_with_a_reason_and_release_the_stock() throws Exception {
        Shop s = shop("Loja Cancela", true);
        String filter = product(s, "FLT-OL-01", "Filtro de óleo", 1200, true, 5);
        placeAndGetToken(s, filter, 3);
        String id = orderId(s, 1);
        assertThat(available(s, filter)).isEqualTo(2);

        for (String reason : new String[] {"", "   ", "null"}) {
            String body = reason.equals("null") ? "{\"reason\":null,\"version\":0}"
                    : "{\"reason\":\"" + reason + "\",\"version\":0}";
            call(post("/api/v1/shop-orders/" + id + "/cancel").content(body), s.owner())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("cancel-reason-required"))
                    .andExpect(jsonPath("$.errors.reason").exists());
        }
        call(post("/api/v1/shop-orders/" + id + "/cancel")
                .content("{\"reason\":\"" + "x".repeat(501) + "\",\"version\":0}"), s.owner())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reason").exists());

        call(post("/api/v1/shop-orders/" + id + "/cancel").content("{\"reason\":\"Sem stock real\",\"version\":0}"),
                s.owner())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("Sem stock real"))
                .andExpect(jsonPath("$.cancelledBy").value("STAFF"));
        assertThat(available(s, filter)).isEqualTo(5);
        assertThat(onHand(s, filter)).isEqualTo(5);
    }

    @Test
    void should_need_the_version_read() throws Exception {
        Shop s = shop("Loja Versao", true);
        String filter = product(s, "FLT-OL-01", "Filtro de óleo", 1200, true, 5);
        placeAndGetToken(s, filter, 1);
        String id = orderId(s, 1);

        call(post("/api/v1/shop-orders/" + id + "/ready").content("{\"version\":3}"), s.owner())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("stale-data"));
        call(post("/api/v1/shop-orders/" + id + "/ready").content("{}"), s.owner())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.version").exists());
        call(post("/api/v1/shop-orders/" + id + "/cancel").content("{\"reason\":\"x\",\"version\":3}"), s.owner())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("stale-data"));
        call(get("/api/v1/shop-orders/" + id), s.owner()).andExpect(jsonPath("$.status").value("PLACED"));
    }

    @Test
    void should_list_orders_newest_first_by_status_and_day_of_placement() throws Exception {
        Shop s = shop("Loja Lista Balcao", true);
        String filter = product(s, "FLT-OL-01", "Filtro de óleo", 1200, true, 10);
        placeAndGetToken(s, filter, 1);
        placeAndGetToken(s, filter, 2);
        call(post("/api/v1/shop-orders/" + orderId(s, 1) + "/ready").content("{\"version\":0}"), s.owner())
                .andExpect(status().isOk());

        call(get("/api/v1/shop-orders"), s.owner())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].number").value(2))
                .andExpect(jsonPath("$[0].status").value("PLACED"))
                .andExpect(jsonPath("$[0].name").value("Ana Silva"))
                .andExpect(jsonPath("$[0].lineCount").value(1))
                .andExpect(jsonPath("$[0].totalCents").value(2952))
                .andExpect(jsonPath("$[0].version").value(0))
                .andExpect(jsonPath("$[0].placedAt").exists())
                .andExpect(jsonPath("$[0].holdUntil").exists())
                .andExpect(jsonPath("$[0].email").doesNotExist())
                .andExpect(jsonPath("$[1].number").value(1));
        call(get("/api/v1/shop-orders").param("status", "READY"), s.owner())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].number").value(1));
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Lisbon"));
        call(get("/api/v1/shop-orders").param("from", today.minusDays(10).toString())
                .param("to", today.minusDays(1).toString()), s.owner())
                .andExpect(jsonPath("$.length()").value(0));
        call(get("/api/v1/shop-orders").param("from", today.minusDays(124).toString())
                .param("to", today.toString()), s.owner())
                .andExpect(jsonPath("$.length()").value(2));
        call(get("/api/v1/shop-orders").param("from", today.minusDays(125).toString())
                .param("to", today.toString()), s.owner())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation"))
                .andExpect(jsonPath("$.errors.to").exists());
    }

    @Test
    void should_read_default_settings_and_save_them_with_versions() throws Exception {
        Shop s = shop("Loja Definicoes", true);
        String desk = member(s, "FRONT_DESK");

        call(get("/api/v1/shop/settings"), desk)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holdDays").value(3))
                .andExpect(jsonPath("$.pickupNote").doesNotExist())
                .andExpect(jsonPath("$.version").value(0));
        call(put("/api/v1/shop/settings").content("{\"holdDays\":5,\"pickupNote\":\"x\",\"version\":0}"), desk)
                .andExpect(status().isForbidden());

        for (String body : new String[] {"{\"holdDays\":0,\"version\":0}", "{\"holdDays\":15,\"version\":0}",
                "{\"version\":0}", "{\"holdDays\":3,\"pickupNote\":\"" + "x".repeat(501) + "\",\"version\":0}",
                "{\"holdDays\":3}"}) {
            call(put("/api/v1/shop/settings").content(body), s.owner())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("validation"));
        }

        call(put("/api/v1/shop/settings")
                .content("{\"holdDays\":1,\"pickupNote\":\"Levante ao balcão, das 9h às 18h.\",\"version\":0}"),
                s.owner())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holdDays").value(1))
                .andExpect(jsonPath("$.pickupNote").value("Levante ao balcão, das 9h às 18h."))
                .andExpect(jsonPath("$.version").value(1));
        call(put("/api/v1/shop/settings").content("{\"holdDays\":2,\"version\":0}"), s.owner())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("stale-data"));

        // New orders are held for the saved days, and the status link shows the note.
        String filter = product(s, "FLT-OL-01", "Filtro de óleo", 1200, true, 5);
        String token = placeAndGetToken(s, filter, 1);
        String status = publicCall(get(s.publicPath("/orders/" + token)))
                .andExpect(jsonPath("$.pickupNote").value("Levante ao balcão, das 9h às 18h."))
                .andReturn().getResponse().getContentAsString();
        assertThat(Duration.between(Instant.parse(JsonPath.read(status, "$.placedAt")),
                Instant.parse(JsonPath.read(status, "$.holdUntil")))).isEqualTo(Duration.ofDays(1));
    }

    @Test
    void should_keep_shop_orders_from_mechanics() throws Exception {
        Shop s = shop("Loja Mecanico", true);
        String mechanic = member(s, "MECHANIC");
        String filter = product(s, "FLT-OL-01", "Filtro de óleo", 1200, true, 5);
        placeAndGetToken(s, filter, 1);
        String id = orderId(s, 1);

        for (MockHttpServletRequestBuilder request : List.of(get("/api/v1/shop-orders"),
                get("/api/v1/shop-orders/" + id), get("/api/v1/shop-orders/pending-count"),
                post("/api/v1/shop-orders/" + id + "/ready").content("{\"version\":0}"),
                post("/api/v1/shop-orders/" + id + "/collect").content("{\"paymentMethod\":\"CASH\",\"version\":0}"),
                post("/api/v1/shop-orders/" + id + "/cancel").content("{\"reason\":\"x\",\"version\":0}"),
                get("/api/v1/shop/settings"), put("/api/v1/shop/settings").content("{\"holdDays\":5,\"version\":0}"))) {
            call(request, mechanic).andExpect(status().isForbidden());
        }
        call(get("/api/v1/shop-orders/" + id), s.owner()).andExpect(jsonPath("$.status").value("PLACED"));
    }

    @Test
    void should_keep_orders_and_settings_inside_their_workshop() throws Exception {
        Shop a = shop("Loja Isolada A", true);
        Shop b = shop("Loja Isolada B", true);
        String filter = product(a, "FLT-OL-01", "Filtro de óleo", 1200, true, 5);
        placeAndGetToken(a, filter, 1);
        String id = orderId(a, 1);
        call(put("/api/v1/shop/settings").content("{\"holdDays\":7,\"pickupNote\":\"Nota A\",\"version\":0}"),
                a.owner()).andExpect(status().isOk());

        call(get("/api/v1/shop-orders"), b.owner()).andExpect(jsonPath("$.length()").value(0));
        call(get("/api/v1/shop-orders/pending-count"), b.owner()).andExpect(jsonPath("$.count").value(0));
        for (ResultActions result : List.of(call(get("/api/v1/shop-orders/" + id), b.owner()),
                call(post("/api/v1/shop-orders/" + id + "/ready").content("{\"version\":0}"), b.owner()),
                call(post("/api/v1/shop-orders/" + id + "/collect").content("{\"paymentMethod\":\"CASH\",\"version\":0}"),
                        b.owner()),
                call(post("/api/v1/shop-orders/" + id + "/cancel").content("{\"reason\":\"x\",\"version\":0}"),
                        b.owner()))) {
            result.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("shop-order-not-found"));
        }
        call(get("/api/v1/shop/settings"), b.owner())
                .andExpect(jsonPath("$.holdDays").value(3))
                .andExpect(jsonPath("$.version").value(0));
        // B's first save creates B's own row; A keeps its settings.
        call(put("/api/v1/shop/settings").content("{\"holdDays\":2,\"version\":0}"), b.owner())
                .andExpect(status().isOk());
        call(get("/api/v1/shop/settings"), a.owner())
                .andExpect(jsonPath("$.holdDays").value(7))
                .andExpect(jsonPath("$.pickupNote").value("Nota A"));
        call(get("/api/v1/shop-orders/" + id), a.owner()).andExpect(jsonPath("$.status").value("PLACED"));
        assertThat(available(a, filter)).isEqualTo(4);
    }
}

package com.asp.platform.shop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.asp.platform.shared.StatusTokens;
import com.jayway.jsonpath.JsonPath;

/// The shop pages of a workshop's public site: the products listed online, placing an order (reserved at once, all
/// or nothing), the status link and the customer's cancel, with the honeypot, the privacy consent, the NIF of a
/// Portuguese workshop, the add-on switch, the rate limit and the race for the last unit.
class PublicShopApiTests extends ShopTestSupport {

    @Test
    void should_list_active_products_listed_online_with_the_price_with_vat_and_a_word_for_the_stock() throws Exception {
        Shop s = shop("Loja Lista", true);
        product(s, "FLT-OL-01", "Filtro de óleo", 1200, true, 10);
        product(s, "PAS-01", "Pastilhas de travão", 3000, true, 2);
        product(s, "LAMP-01", "Lâmpada H7", 500, true, 0);
        product(s, "BAL-01", "Só ao balcão", 1000, false, 10);
        String retired = product(s, "OLD-01", "Retirado", 1000, true, 10);
        call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/stock/products/" + retired)
                .content(withVersion("""
                        {"sku":"OLD-01","name":"Retirado","costCents":500,"salePriceCents":1000,"vatRate":23,
                         "active":false,"listedOnline":true}""", 0)), s.owner()).andExpect(status().isOk());

        publicCall(get(s.publicPath("/products")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].name").value("Filtro de óleo"))
                .andExpect(jsonPath("$[0].sku").value("FLT-OL-01"))
                .andExpect(jsonPath("$[0].brand").value("Bosch"))
                .andExpect(jsonPath("$[0].unit").value("un"))
                .andExpect(jsonPath("$[0].unitPriceWithVatCents").value(1476))
                .andExpect(jsonPath("$[0].availability").value("IN_STOCK"))
                .andExpect(jsonPath("$[0].available").doesNotExist())
                .andExpect(jsonPath("$[0].onHand").doesNotExist())
                .andExpect(jsonPath("$[1].name").value("Lâmpada H7"))
                .andExpect(jsonPath("$[1].availability").value("OUT"))
                .andExpect(jsonPath("$[2].availability").value("LAST_UNITS"));
        publicCall(get(s.publicPath("/products")).param("q", "BOSCH")).andExpect(jsonPath("$.length()").value(3));
        publicCall(get(s.publicPath("/products")).param("q", "flt-ol")).andExpect(jsonPath("$.length()").value(1));
        publicCall(get(s.publicPath("/products")).param("q", "x".repeat(61)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation"))
                .andExpect(jsonPath("$.errors.q").exists());
    }

    @Test
    void should_place_an_order_reserve_its_stock_show_its_status_and_let_the_customer_cancel() throws Exception {
        Shop s = shop("Loja Encomenda", true);
        String filter = product(s, "FLT-OL-01", "Filtro de óleo", 1200, true, 5);

        String answer = order(s, orderBody(filter, 2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.totalCents").value(2952))
                .andExpect(jsonPath("$.holdUntil").exists())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(answer, "$.statusToken");
        assertThat(token).hasSize(StatusTokens.LENGTH);
        assertThat(available(s, filter)).isEqualTo(3);
        assertThat(onHand(s, filter)).isEqualTo(5);

        publicCall(get(s.publicPath("/orders/" + token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.status").value("PLACED"))
                .andExpect(jsonPath("$.timeZone").value("Europe/Lisbon"))
                .andExpect(jsonPath("$.workshopName").value("Loja Encomenda"))
                .andExpect(jsonPath("$.pickupNote").doesNotExist())
                .andExpect(jsonPath("$.lines[0].name").value("Filtro de óleo"))
                .andExpect(jsonPath("$.lines[0].unit").value("un"))
                .andExpect(jsonPath("$.lines[0].quantity").value(2))
                .andExpect(jsonPath("$.lines[0].unitPriceWithVatCents").value(1476))
                .andExpect(jsonPath("$.lines[0].lineTotalCents").value(2952))
                .andExpect(jsonPath("$.lines[0].productId").doesNotExist())
                .andExpect(jsonPath("$.totalCents").value(2952))
                .andExpect(jsonPath("$.canCancel").value(true))
                .andExpect(jsonPath("$.name").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.id").doesNotExist());

        publicCall(post(s.publicPath("/orders/" + token + "/cancel")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.canCancel").value(false));
        assertThat(available(s, filter)).isEqualTo(5);
        publicCall(post(s.publicPath("/orders/" + token + "/cancel")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("shop-order-not-open"));
        call(get("/api/v1/shop-orders/" + orderId(s, 1)), s.owner())
                .andExpect(jsonPath("$.cancelledBy").value("CUSTOMER"))
                .andExpect(jsonPath("$.cancelReason").doesNotExist());
    }

    @Test
    void should_let_the_customer_cancel_only_while_the_order_is_placed() throws Exception {
        Shop s = shop("Loja Pronta", true);
        String filter = product(s, "FLT-OL-01", "Filtro de óleo", 1200, true, 5);
        String token = placeAndGetToken(s, filter, 1);
        call(post("/api/v1/shop-orders/" + orderId(s, 1) + "/ready").content("{\"version\":0}"), s.owner())
                .andExpect(status().isOk());

        publicCall(get(s.publicPath("/orders/" + token)))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.canCancel").value(false));
        publicCall(post(s.publicPath("/orders/" + token + "/cancel")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("shop-order-not-open"));
        assertThat(available(s, filter)).isEqualTo(4);
    }

    @Test
    void should_give_the_last_unit_to_one_of_two_orders_racing_for_it() throws Exception {
        Shop s = shop("Loja Corrida", true);
        String filter = product(s, "FLT-OL-01", "Filtro de óleo", 1200, true, 1);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return order(s, orderBody(filter, 1)).andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get(30, TimeUnit.SECONDS));
            }
            Collections.sort(statuses);
            assertThat(statuses).containsExactly(201, 409);
        } finally {
            pool.shutdownNow();
        }
        call(get("/api/v1/shop-orders"), s.owner()).andExpect(jsonPath("$.length()").value(1));
        assertThat(available(s, filter)).isZero();
    }

    @Test
    void should_store_nothing_when_one_line_cannot_be_reserved() throws Exception {
        Shop s = shop("Loja Tudo Ou Nada", true);
        String filter = product(s, "FLT-OL-01", "Filtro de óleo", 1200, true, 5);
        String pads = product(s, "PAS-01", "Pastilhas", 3000, true, 1);

        order(s, orderBody(filter, 2, pads, 2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("insufficient-stock"))
                .andExpect(jsonPath("$.productId").value(pads));

        assertThat(available(s, filter)).isEqualTo(5);
        assertThat(available(s, pads)).isEqualTo(1);
        call(get("/api/v1/shop-orders"), s.owner()).andExpect(jsonPath("$.length()").value(0));
        // The next order still gets number 1.
        order(s, orderBody(filter, 1)).andExpect(status().isCreated()).andExpect(jsonPath("$.number").value(1));
    }

    @Test
    void should_refuse_products_that_are_not_sold_online() throws Exception {
        Shop s = shop("Loja Fora", true);
        String filter = product(s, "FLT-OL-01", "Filtro de óleo", 1200, true, 5);
        String counterOnly = product(s, "BAL-01", "Só ao balcão", 1000, false, 5);
        Shop other = shop("Loja Fora Outra", true);
        String othersProduct = product(other, "OUT-01", "Da outra", 1000, true, 5);

        for (String id : new String[] {counterOnly, othersProduct, "6f1c1d1e-0000-4000-8000-000000000000"}) {
            order(s, orderBody(filter, 1, id, 1))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("product-not-listed"))
                    .andExpect(jsonPath("$.productId").value(id));
        }
        assertThat(available(s, filter)).isEqualTo(5);
        assertThat(available(other, othersProduct)).isEqualTo(5);
    }

    @Test
    void should_answer_a_filled_hidden_field_like_a_success_and_store_nothing() throws Exception {
        Shop s = shop("Loja Robo", true);
        String filter = product(s, "FLT-OL-01", "Filtro de óleo", 1200, true, 5);

        String answer = order(s, orderBody(filter, 2).replace("\"website\":\"\"", "\"website\":\"http://spam\""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").isNumber())
                .andExpect(jsonPath("$.holdUntil").exists())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(answer, "$.statusToken");
        assertThat(token).hasSize(StatusTokens.LENGTH);

        call(get("/api/v1/shop-orders"), s.owner()).andExpect(jsonPath("$.length()").value(0));
        assertThat(available(s, filter)).isEqualTo(5);
        publicCall(get(s.publicPath("/orders/" + token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("shop-order-not-found"));
    }

    @Test
    void should_need_the_privacy_consent_before_anything_else() throws Exception {
        Shop s = shop("Loja Privacidade", true);
        String filter = product(s, "FLT-OL-01", "Filtro de óleo", 1200, true, 5);

        for (String consent : new String[] {"false", "null"}) {
            order(s, orderBody(filter, 1).replace("\"privacyAccepted\":true", "\"privacyAccepted\":" + consent))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("privacy-consent-required"))
                    .andExpect(jsonPath("$.errors.privacyAccepted").exists());
        }
        // Checked before the hidden field: a bot without the consent gets the same answer as a person.
        order(s, orderBody(filter, 1).replace("\"privacyAccepted\":true", "\"privacyAccepted\":false")
                .replace("\"website\":\"\"", "\"website\":\"x\"")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("privacy-consent-required"));
        call(get("/api/v1/shop-orders"), s.owner()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void should_check_the_nif_of_a_portuguese_workshop() throws Exception {
        Shop s = shop("Loja NIF", true);
        String filter = product(s, "FLT-OL-01", "Filtro de óleo", 1200, true, 5);

        order(s, orderBody(filter, 1).replace("123456789", "123456780"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid-nif"))
                .andExpect(jsonPath("$.errors.nif").exists());
        assertThat(available(s, filter)).isEqualTo(5);
        // The NIF is optional.
        order(s, orderBody(filter, 1).replace("\"nif\":\"123456789\"", "\"nif\":null"))
                .andExpect(status().isCreated());
    }

    @Test
    void should_validate_the_order_form() throws Exception {
        Shop s = shop("Loja Validacao", true);
        String filter = product(s, "FLT-OL-01", "Filtro de óleo", 1200, true, 50);
        String valid = orderBody(filter, 1);

        expectInvalid(s, orderBody(), "lines");
        expectInvalid(s, orderBody(filter, 0), "lines");
        expectInvalid(s, orderBody(filter, 21), "lines");
        expectInvalid(s, orderBody(filter, 1, filter, 2), "lines");
        expectInvalid(s, valid.replace("\"productId\":\"" + filter + "\",", ""), "lines");
        List<Object> many = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            many.add(java.util.UUID.randomUUID());
            many.add(1);
        }
        expectInvalid(s, orderBody(many.toArray()), "lines");
        expectInvalid(s, valid.replace("\"Ana Silva\"", "\"Ganhou! Abra https://evil.example\""), "name");
        expectInvalid(s, valid.replace("\"Ana Silva\"", "\"Veja www.evil.example\""), "name");
        expectInvalid(s, valid.replace("\"Ana Silva\"", "\"Ana\\nSilva\""), "name");
        expectInvalid(s, valid.replace("\"Ana Silva\"", "\"" + "x".repeat(121) + "\""), "name");
        expectInvalid(s, valid.replace("\"phone\":\"912345678\"", "\"phone\":\"\"")
                .replace("\"email\":\"ana@example.pt\"", "\"email\":null"), "phone")
                .andExpect(jsonPath("$.errors.email").exists());
        expectInvalid(s, valid.replace("912345678", "ligue-me já"), "phone");
        expectInvalid(s, valid.replace("ana@example.pt", "not-an-email"), "email");
        expectInvalid(s, valid.replace("\"language\":\"pt-PT\"", "\"language\":\"fr\""), "language");

        // A phone alone, or an e-mail alone, is enough.
        order(s, valid.replace("\"phone\":\"912345678\"", "\"phone\":null")).andExpect(status().isCreated());
        order(s, valid.replace("\"email\":\"ana@example.pt\"", "\"email\":null")).andExpect(status().isCreated());
    }

    @Test
    void should_answer_add_on_not_available_on_every_shop_page_while_the_add_on_is_off() throws Exception {
        Shop s = shop("Loja Desligada", false);
        String filter = product(s, "FLT-OL-01", "Filtro de óleo", 1200, true, 5);

        for (ResultActions result : List.of(publicCall(get(s.publicPath("/products"))),
                order(s, orderBody(filter, 1)),
                publicCall(get(s.publicPath("/orders/" + StatusTokens.generate()))),
                publicCall(post(s.publicPath("/orders/" + StatusTokens.generate() + "/cancel"))))) {
            result.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("add-on-not-available"));
        }
        assertThat(available(s, filter)).isEqualTo(5);
    }

    @Test
    void should_keep_status_links_inside_their_workshop() throws Exception {
        Shop a = shop("Loja Link A", true);
        Shop b = shop("Loja Link B", true);
        String filter = product(a, "FLT-OL-01", "Filtro de óleo", 1200, true, 5);
        String token = placeAndGetToken(a, filter, 1);

        publicCall(get(b.publicPath("/orders/" + token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("shop-order-not-found"));
        publicCall(post(b.publicPath("/orders/" + token + "/cancel"))).andExpect(status().isNotFound());
        publicCall(get(a.publicPath("/orders/" + token))).andExpect(jsonPath("$.status").value("PLACED"));
        publicCall(get(a.publicPath("/orders/not-a-token"))).andExpect(status().isNotFound());
        // Another workshop's page does not list its products either.
        publicCall(get(b.publicPath("/products"))).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void should_count_orders_against_the_public_write_limit() throws Exception {
        Shop s = shop("Loja Limite", true);
        String filter = product(s, "FLT-OL-01", "Filtro de óleo", 1200, true, 50);
        String address = randomAddress();

        // Test limit: 3 writes an hour per address and workshop.
        for (int i = 0; i < 3; i++) {
            mvc.perform(fromClient(address, post(s.publicPath("/orders")).content(orderBody(filter, 1))))
                    .andExpect(status().isCreated());
        }
        mvc.perform(fromClient(address, post(s.publicPath("/orders")).content(orderBody(filter, 1))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("too-many-requests"));
        // Reads have their own budget.
        mvc.perform(fromClient(address, get(s.publicPath("/products")))).andExpect(status().isOk());
        assertThat(available(s, filter)).isEqualTo(47);
    }

    private ResultActions expectInvalid(Shop s, String body, String field) throws Exception {
        return order(s, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation"))
                .andExpect(jsonPath("$.errors." + field).exists());
    }
}

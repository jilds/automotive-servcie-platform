package com.asp.platform.workshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.asp.platform.shared.StatusTokens;
import com.jayway.jsonpath.JsonPath;

/// The customer's quote link (`/api/v1/public/{slug}/quotes/{token}`): what it shows (no customer data, no ids),
/// approving with a name and the acceptance, rejecting with an optional reason, the order moves, closed and expired
/// quotes, and unknown tokens and slugs. No add-on is needed.
class PublicQuoteApiTests extends QuoteTestSupport {

    @Test
    void should_show_the_quote_without_customer_data_or_ids() throws Exception {
        Shop g = garage("Oficina Link");
        String wo = quotableOrder(g, true);
        String token = tokenOfQuote(sendQuote(g, wo));

        String body = publicCall(get(publicQuote(g, token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.workOrderNumber").value(1))
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.timeZone").value("Europe/Lisbon"))
                .andExpect(jsonPath("$.workshopName").value("Oficina Link"))
                .andExpect(jsonPath("$.vehicle.plateDisplay").value(PLATE))
                .andExpect(jsonPath("$.vehicle.plateEra").value("PT_2020"))
                .andExpect(jsonPath("$.vehicle.registrationCountry").value("PT"))
                .andExpect(jsonPath("$.vehicle.label").value("Renault Clio"))
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andExpect(jsonPath("$.lines[0].description").value("Mudança de óleo"))
                .andExpect(jsonPath("$.lines[1].netCents").value(2250))
                .andExpect(jsonPath("$.vatByRate[0].vatRate").value(23))
                .andExpect(jsonPath("$.netCents").value(6250))
                .andExpect(jsonPath("$.vatCents").value(1438))
                .andExpect(jsonPath("$.totalCents").value(7688))
                .andExpect(jsonPath("$.sentAt").exists())
                .andExpect(jsonPath("$.validUntil").exists())
                .andExpect(jsonPath("$.decidedAt").isEmpty())
                .andExpect(jsonPath("$.canDecide").value(true))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("Ana Pereira", "ana@cliente.pt", "123456789", wo, "\"id\"",
                "productId", "serviceItemId", "workOrderId");
        Map<String, Object> line = JsonPath.read(body, "$.lines[0]");
        assertThat(line).containsOnlyKeys("type", "description", "quantity", "unitPriceCents", "discountPercent",
                "vatRate", "netCents");
    }

    @Test
    void should_approve_with_a_name_and_the_acceptance_and_move_the_order() throws Exception {
        Shop g = garage("Oficina Aprova");
        String wo = quotableOrder(g, false);
        String token = tokenOfQuote(sendQuote(g, wo));
        String path = publicQuote(g, token) + "/approve";

        for (String bad : new String[] {
                "{\"accepted\":true}",
                "{\"name\":\" \",\"accepted\":true}",
                "{\"name\":\"Ana http://phish.example\",\"accepted\":true}",
                "{\"name\":\"Ana www.phish.pt\",\"accepted\":true}",
                "{\"name\":\"Ana\\nSilva\",\"accepted\":true}",
                "{\"name\":\"<b>Ana</b>\",\"accepted\":true}",
                "{\"name\":\"" + "a".repeat(121) + "\",\"accepted\":true}"}) {
            publicCall(post(path).content(bad))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("validation"))
                    .andExpect(jsonPath("$.errors.name").exists());
        }
        for (String bad : new String[] {"{\"name\":\"Ana Silva\"}", "{\"name\":\"Ana Silva\",\"accepted\":false}"}) {
            publicCall(post(path).content(bad))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.accepted").exists());
        }

        approveOnLink(g, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decidedChannel").value("CUSTOMER"))
                .andExpect(jsonPath("$.decidedAt").exists())
                .andExpect(jsonPath("$.canDecide").value(false));
        assertThat(workOrderStatus(g, wo)).isEqualTo("IN_PROGRESS");
        call(get("/api/v1/work-orders/" + wo + "/quotes"), g.owner())
                .andExpect(jsonPath("$[0].decidedName").value("Ana Silva"))
                .andExpect(jsonPath("$[0].decidedChannel").value("CUSTOMER"))
                .andExpect(jsonPath("$[0].decidedByName").isEmpty());

        approveOnLink(g, token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("quote-not-open"));
        publicCall(post(publicQuote(g, token) + "/reject").content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("quote-not-open"));
    }

    @Test
    void should_move_to_awaiting_parts_when_a_part_was_typed_by_hand() throws Exception {
        Shop g = garage("Oficina Aprova Pecas");
        String wo = quotableOrder(g, true);
        String token = tokenOfQuote(sendQuote(g, wo));

        approveOnLink(g, token).andExpect(status().isOk());

        assertThat(workOrderStatus(g, wo)).isEqualTo("AWAITING_PARTS");
    }

    @Test
    void should_reject_with_an_optional_reason_without_moving_the_order() throws Exception {
        Shop g = garage("Oficina Rejeita");
        String wo = quotableOrder(g, false);
        String token = tokenOfQuote(sendQuote(g, wo));

        publicCall(post(publicQuote(g, token) + "/reject").content("{\"reason\":\"" + "x".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reason").exists());
        // Control characters other than tab and line feed (here BEL, CR, NEL) and U+2028 are refused.
        for (String control : new String[] {"\\u0007", "\\r", "\\u0085", "\\u2028"}) {
            publicCall(post(publicQuote(g, token) + "/reject").content("{\"reason\":\"Vou" + control + "pensar\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("validation"))
                    .andExpect(jsonPath("$.errors.reason").exists());
        }
        publicCall(post(publicQuote(g, token) + "/reject").content("{\"reason\":\"Vou pensar\\n\\tmais tarde\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.decidedChannel").value("CUSTOMER"))
                .andExpect(jsonPath("$.canDecide").value(false));
        assertThat(workOrderStatus(g, wo)).isEqualTo("AWAITING_APPROVAL");
        call(get("/api/v1/work-orders/" + wo + "/quotes"), g.owner())
                .andExpect(jsonPath("$[0].decidedNote").value("Vou pensar\n\tmais tarde"))
                .andExpect(jsonPath("$[0].decidedName").isEmpty());

        // Without a reason, and without a body.
        String second = tokenOfQuote(sendQuote(g, wo));
        publicCall(post(publicQuote(g, second) + "/reject").content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        String third = tokenOfQuote(sendQuote(g, wo));
        mvc.perform(fromClient(randomAddress(), post(publicQuote(g, third) + "/reject")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void should_close_a_superseded_link_and_refuse_an_expired_one() throws Exception {
        Shop g = garage("Oficina Fechado");
        String wo = quotableOrder(g, false);
        String first = tokenOfQuote(sendQuote(g, wo));
        String secondBody = sendQuote(g, wo);
        String second = tokenOfQuote(secondBody);

        publicCall(get(publicQuote(g, first)))
                .andExpect(jsonPath("$.status").value("SUPERSEDED"))
                .andExpect(jsonPath("$.canDecide").value(false));
        approveOnLink(g, first)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("quote-not-open"));

        pastValidity(g, idOfQuote(secondBody));
        publicCall(get(publicQuote(g, second)))
                .andExpect(jsonPath("$.status").value("EXPIRED"))
                .andExpect(jsonPath("$.canDecide").value(false));
        approveOnLink(g, second)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("quote-expired"));
        assertThat(storedStatus(g, idOfQuote(secondBody))).isEqualTo("EXPIRED");
        assertThat(workOrderStatus(g, wo)).isEqualTo("AWAITING_APPROVAL");
        approveOnLink(g, second)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("quote-not-open"));
    }

    @Test
    void should_answer_404_for_unknown_tokens_other_workshops_and_unknown_slugs() throws Exception {
        Shop a = garage("Oficina Token A");
        Shop b = garage("Oficina Token B");
        String token = tokenOfQuote(sendQuote(a, quotableOrder(a, false)));

        publicCall(get(publicQuote(a, StatusTokens.generate())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("quote-not-found"));
        publicCall(get(publicQuote(b, token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("quote-not-found"));
        approveOnLink(b, token)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("quote-not-found"));
        publicCall(get("/api/v1/public/no-such-workshop/quotes/" + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("workshop-not-found"));
        publicCall(get(publicQuote(a, token))).andExpect(jsonPath("$.status").value("SENT"));
    }
}

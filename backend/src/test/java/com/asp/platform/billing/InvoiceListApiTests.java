package com.asp.platform.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;

class InvoiceListApiTests extends BillingTestSupport {

    private String list(Shop g, String query) throws Exception {
        return call(get("/api/v1/invoices" + query), g.owner()).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
    }

    @Test
    void should_list_newest_first_with_paging_and_filters() throws Exception {
        Shop g = garage("Lista Um");
        String first = issuedInvoice(g);
        String second = issuedInvoice(g);
        String third = issuedInvoice(g);
        cash(g, first, TOTAL);
        cash(g, second, 1000);
        String note = idOf(call(post("/api/v1/invoices/" + third + "/credit-notes")
                .content("{\"requestId\":\"" + newRequestId() + "\",\"reason\":\"Erro\"}"), g.owner())
                .andExpect(status().isCreated()));

        String all = list(g, "");
        assertThat((List<String>) JsonPath.read(all, "$.items[*].id")).containsExactly(note, third, second, first);
        assertThat((Number) JsonPath.read(all, "$.total")).isEqualTo(4);
        assertThat((Number) JsonPath.read(all, "$.page")).isEqualTo(0);
        assertThat((Number) JsonPath.read(all, "$.size")).isEqualTo(20);
        assertThat((String) JsonPath.read(all, "$.items[0].kind")).isEqualTo("CREDIT_NOTE");
        assertThat((String) JsonPath.read(all, "$.items[3].customerName")).isEqualTo("Ana Pereira");
        assertThat((String) JsonPath.read(all, "$.items[3].customerNif")).isEqualTo("123456789");
        assertThat((Number) JsonPath.read(all, "$.items[3].totalCents")).isEqualTo(7688);
        assertThat((Boolean) JsonPath.read(all, "$.items[3].sandbox")).isTrue();

        String page = list(g, "?size=2&page=1");
        assertThat((List<String>) JsonPath.read(page, "$.items[*].id")).containsExactly(second, first);
        assertThat((Number) JsonPath.read(page, "$.total")).isEqualTo(4);

        assertThat((List<String>) JsonPath.read(list(g, "?kind=CREDIT_NOTE"), "$.items[*].id")).containsExactly(note);
        assertThat((List<String>) JsonPath.read(list(g, "?kind=INVOICE&paymentStatus=PAID"), "$.items[*].id"))
                .containsExactly(first);
        assertThat((List<String>) JsonPath.read(list(g, "?paymentStatus=PARTIALLY_PAID"), "$.items[*].id"))
                .containsExactly(second);
        assertThat((List<String>) JsonPath.read(list(g, "?paymentStatus=CREDITED"), "$.items[*].id"))
                .containsExactly(third);
        assertThat((List<String>) JsonPath.read(list(g, "?status=ISSUED&status=VOIDED&kind=INVOICE"), "$.items[*].id"))
                .containsExactly(third, second, first);
        assertThat((Number) JsonPath.read(list(g, "?status=VOIDED"), "$.total")).isEqualTo(0);
    }

    @Test
    void should_search_by_number_name_nif_and_plate_and_filter_by_date() throws Exception {
        Shop g = garage("Lista Dois");
        String id = issuedInvoice(g);
        String today = LocalDate.now(ZoneId.of("Europe/Lisbon")).toString();

        for (String q : List.of("SANDBOX/1", "sandbox/1", "pereira", "123456789", "AA-00")) {
            assertThat((List<String>) JsonPath.read(list(g, "?q=" + q), "$.items[*].id"))
                    .as(q).contains(id);
        }
        assertThat((Number) JsonPath.read(list(g, "?q=zzzzzz"), "$.total")).isEqualTo(0);
        assertThat(((Number) JsonPath.read(list(g, "?from=" + today + "&to=" + today), "$.total")).intValue())
                .isGreaterThanOrEqualTo(1);
        assertThat((Number) JsonPath.read(list(g, "?to=2020-01-01"), "$.total")).isEqualTo(0);
        assertThat((Number) JsonPath.read(list(g, "?from=2999-01-01"), "$.total")).isEqualTo(0);
    }

    @Test
    void should_refuse_bad_query_values() throws Exception {
        Shop g = garage("Lista Tres");

        for (String query : List.of("?size=0", "?size=101", "?page=-1", "?q=a", "?from=ontem", "?kind=OTHER",
                "?status=NOPE", "?paymentStatus=NOPE")) {
            call(get("/api/v1/invoices" + query), g.owner()).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("validation"));
        }
    }
}

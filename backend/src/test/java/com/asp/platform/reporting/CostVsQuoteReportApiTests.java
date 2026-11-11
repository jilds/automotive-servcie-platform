package com.asp.platform.reporting;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import com.jayway.jsonpath.JsonPath;

/// `GET /api/v1/reports/cost-vs-quote` on the fixed workshop of [ReportTestSupport#seedScenario()].
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CostVsQuoteReportApiTests extends ReportTestSupport {

    private Scenario s;

    @BeforeAll
    void seed() throws Exception {
        s = seedScenario();
    }

    @Test
    void should_list_the_delivered_orders_of_the_period_newest_first_and_summarise_them() throws Exception {
        report(s.shop(), "cost-vs-quote", PERIOD).andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-11-01"))
                .andExpect(jsonPath("$.to").value("2026-11-30"))
                .andExpect(jsonPath("$.timezone").value("Europe/Lisbon"))
                .andExpect(jsonPath("$.labourCostSet").value(true))
                .andExpect(jsonPath("$.marginBasis").value("PARTS_AND_LABOUR"))
                .andExpect(jsonPath("$.summary.orders").value(5))
                .andExpect(jsonPath("$.summary.ordersWithApprovedQuote").value(1))
                .andExpect(jsonPath("$.summary.quoteTotalCents").value(4920))
                .andExpect(jsonPath("$.summary.finalTotalCents").value(7995))
                .andExpect(jsonPath("$.summary.differenceCents").value(3075))
                .andExpect(jsonPath("$.summary.differencePercent").value(62.5))
                .andExpect(jsonPath("$.summary.standardHours").value(3.5))
                .andExpect(jsonPath("$.summary.trackedHours").value(6.5))
                .andExpect(jsonPath("$.summary.netCents").value(23500))
                .andExpect(jsonPath("$.summary.partsCostCents").value(1600))
                .andExpect(jsonPath("$.summary.labourCostCents").value(19500))
                .andExpect(jsonPath("$.summary.marginCents").value(2400))
                .andExpect(jsonPath("$.summary.marginPercent").value(10.21))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(50))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.items[*].workOrderId", contains(s.o4(), s.o5(), s.o3(), s.o2(), s.o1())));
    }

    @Test
    void should_compare_the_approved_quote_with_the_final_total_and_the_hours_with_the_standard() throws Exception {
        report(s.shop(), "cost-vs-quote", PERIOD + "&mechanicId=" + s.rui().id()).andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[1].workOrderId").value(s.o1()))
                .andExpect(jsonPath("$.items[1].plate").value("AA10AA"))
                .andExpect(jsonPath("$.items[1].deliveredAt").value("2026-11-05T10:00:00Z"))
                .andExpect(jsonPath("$.items[1].mechanicId").value(s.rui().id()))
                .andExpect(jsonPath("$.items[1].mechanicName").value("Rui"))
                .andExpect(jsonPath("$.items[1].quote.number").value(1))
                .andExpect(jsonPath("$.items[1].quote.channel").value("CUSTOMER"))
                .andExpect(jsonPath("$.items[1].quote.netCents").value(4000))
                .andExpect(jsonPath("$.items[1].quote.totalCents").value(4920))
                .andExpect(jsonPath("$.items[1].quote.id").isString())
                .andExpect(jsonPath("$.items[1].quote.approvedAt").isString())
                .andExpect(jsonPath("$.items[1].finalNetCents").value(6500))
                .andExpect(jsonPath("$.items[1].finalVatCents").value(1495))
                .andExpect(jsonPath("$.items[1].finalTotalCents").value(7995))
                .andExpect(jsonPath("$.items[1].differenceCents").value(3075))
                .andExpect(jsonPath("$.items[1].differencePercent").value(62.5))
                .andExpect(jsonPath("$.items[1].standardHours").value(1.5))
                .andExpect(jsonPath("$.items[1].trackedHours").value(3.5))
                .andExpect(jsonPath("$.items[1].partsCostCents").value(1600))
                .andExpect(jsonPath("$.items[1].labourCostCents").value(10500))
                .andExpect(jsonPath("$.items[1].marginCents").value(-5600))
                .andExpect(jsonPath("$.items[1].marginPercent").value(-86.15));
    }

    @Test
    void should_list_an_order_without_a_quote_with_null_comparison_but_its_hours_and_margin() throws Exception {
        report(s.shop(), "cost-vs-quote", PERIOD + "&mechanicId=" + s.rui().id()).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].workOrderId").value(s.o2()))
                .andExpect(jsonPath("$.items[0].quote").doesNotExist())
                .andExpect(jsonPath("$.items[0].differenceCents").doesNotExist())
                .andExpect(jsonPath("$.items[0].differencePercent").doesNotExist())
                .andExpect(jsonPath("$.items[0].finalNetCents").value(9000))
                .andExpect(jsonPath("$.items[0].finalTotalCents").value(11070))
                .andExpect(jsonPath("$.items[0].standardHours").value(2.0))
                .andExpect(jsonPath("$.items[0].trackedHours").value(2.0))
                .andExpect(jsonPath("$.items[0].partsCostCents").value(0))
                .andExpect(jsonPath("$.items[0].labourCostCents").value(6000))
                .andExpect(jsonPath("$.items[0].marginCents").value(3000))
                .andExpect(jsonPath("$.items[0].marginPercent").value(33.33));
    }

    @Test
    void should_show_no_tracked_time_as_zero_hours_and_zero_labour_cost() throws Exception {
        report(s.shop(), "cost-vs-quote", PERIOD + "&mechanicId=" + s.teresa().id()).andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].workOrderId").value(s.o4()))
                .andExpect(jsonPath("$.items[0].trackedHours").value(0.0))
                .andExpect(jsonPath("$.items[0].standardHours").value(0.0))
                .andExpect(jsonPath("$.items[0].labourCostCents").value(0))
                .andExpect(jsonPath("$.items[0].marginCents").value(2000))
                .andExpect(jsonPath("$.items[0].marginPercent").value(100.0));
    }

    @Test
    void should_filter_by_whether_the_order_has_an_approved_quote() throws Exception {
        report(s.shop(), "cost-vs-quote", PERIOD + "&hasApprovedQuote=true").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].workOrderId").value(s.o1()))
                .andExpect(jsonPath("$.summary.orders").value(1));
        report(s.shop(), "cost-vs-quote", PERIOD + "&hasApprovedQuote=false").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.summary.ordersWithApprovedQuote").value(0))
                .andExpect(jsonPath("$.summary.differencePercent").doesNotExist())
                .andExpect(jsonPath("$.items[*].workOrderId", contains(s.o4(), s.o5(), s.o3(), s.o2())));
    }

    @Test
    void should_page_the_list_but_summarise_the_whole_set() throws Exception {
        report(s.shop(), "cost-vs-quote", PERIOD + "&size=2&page=1").andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.items[*].workOrderId", contains(s.o3(), s.o2())))
                .andExpect(jsonPath("$.summary.orders").value(5))
                .andExpect(jsonPath("$.summary.netCents").value(23500));
        report(s.shop(), "cost-vs-quote", PERIOD + "&size=2&page=2").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].workOrderId", contains(s.o1())));
        report(s.shop(), "cost-vs-quote", PERIOD + "&size=2&page=9").andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void should_refuse_a_bad_page_size_or_filter() throws Exception {
        report(s.shop(), "cost-vs-quote", PERIOD + "&size=0").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation")).andExpect(jsonPath("$.errors.size").exists());
        report(s.shop(), "cost-vs-quote", PERIOD + "&size=201").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.size").exists());
        report(s.shop(), "cost-vs-quote", PERIOD + "&page=-1").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.page").exists());
        report(s.shop(), "cost-vs-quote", PERIOD + "&mechanicId=nope").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.mechanicId").exists());
        report(s.shop(), "cost-vs-quote", PERIOD + "&hasApprovedQuote=maybe").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.hasApprovedQuote").exists());
        report(s.shop(), "cost-vs-quote", PERIOD + "&format=xml").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.format").exists());
    }

    @Test
    void should_refuse_a_bad_period() throws Exception {
        report(s.shop(), "cost-vs-quote", "from=2026-11-10&to=2026-11-09").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation")).andExpect(jsonPath("$.errors.from").exists());
        report(s.shop(), "cost-vs-quote", "from=10-11-2026").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation")).andExpect(jsonPath("$.errors.from").exists());
        report(s.shop(), "cost-vs-quote", "to=tomorrow").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.to").exists());
    }

    @Test
    void should_limit_the_period_to_24_months() throws Exception {
        report(s.shop(), "cost-vs-quote", "from=2025-01-01&to=2026-12-31").andExpect(status().isOk());
        report(s.shop(), "cost-vs-quote", "from=2025-01-01&to=2027-01-01").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("report-range-too-large"))
                .andExpect(jsonPath("$.type").value("/problems/report-range-too-large"))
                .andExpect(jsonPath("$.errors.to").exists());
    }

    @Test
    void should_show_an_order_without_a_mechanic_in_the_period_it_was_delivered() throws Exception {
        report(s.shop(), "cost-vs-quote", "from=2026-02-01&to=2026-02-28").andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].workOrderId").value(s.o0()))
                .andExpect(jsonPath("$.items[0].mechanicId").doesNotExist())
                .andExpect(jsonPath("$.items[0].mechanicName").doesNotExist());
    }

    @Test
    void should_not_report_open_or_cancelled_orders() throws Exception {
        String body = report(s.shop(), "cost-vs-quote", PERIOD).andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(JsonPath.<java.util.List<String>>read(body, "$.items[*].workOrderId"))
                .doesNotContain(s.o7(), s.o8());
    }

    @Test
    void should_return_zeros_and_empty_list_for_an_empty_period() throws Exception {
        report(s.shop(), "cost-vs-quote", "from=2026-05-01&to=2026-05-31").andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.summary.orders").value(0))
                .andExpect(jsonPath("$.summary.netCents").value(0))
                .andExpect(jsonPath("$.summary.marginCents").value(0))
                .andExpect(jsonPath("$.summary.marginPercent").doesNotExist())
                .andExpect(jsonPath("$.summary.differencePercent").doesNotExist());
    }

    @Test
    void should_use_the_approved_quote_and_ignore_a_superseded_one() throws Exception {
        Shop g = garage("Oficina Varios Orcamentos " + java.util.UUID.randomUUID());
        String wo = quotableOrder(g, false);
        // 1: sent (4920) and superseded by the new line; 2: approved (6150); then one more line after the approval.
        sendQuote(g, wo);
        labour(g, wo, "Extra", 1000, 23);
        approveQuote(g, wo);
        labour(g, wo, "Mais", 1000, 23);
        moveOk(g.owner(), wo, "READY");
        moveOk(g.owner(), wo, "DELIVERED");

        report(g, "cost-vs-quote", "from=" + java.time.LocalDate.now(LISBON_ZONE).minusDays(1) + "&to="
                + java.time.LocalDate.now(LISBON_ZONE).plusDays(1)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].quote.number").value(2))
                .andExpect(jsonPath("$.items[0].quote.totalCents").value(6150))
                .andExpect(jsonPath("$.items[0].finalTotalCents").value(7380))
                .andExpect(jsonPath("$.items[0].differenceCents").value(1230))
                .andExpect(jsonPath("$.items[0].differencePercent").value(20.0));
    }

    @Test
    void should_use_the_approved_quote_with_the_highest_number_when_there_are_two() throws Exception {
        Shop g = garage("Oficina Dois Aprovados " + java.util.UUID.randomUUID());
        String wo = quotableOrder(g, false);
        approveQuote(g, wo);
        // The API cannot approve twice (the order has moved on), so a second approved quote, number 2 and dearer, is
        // written as the table owner by copying the first one.
        asOwner(g, c -> {
            try (java.sql.PreparedStatement s = c.prepareStatement("""
                    insert into quote (id, work_order_id, number, status, sent_at, sent_by, valid_until, net_cents,
                                       vat_cents, total_cents, status_token_hash, decided_at, decided_channel,
                                       decided_by, decided_name, decided_note, version, created_at, updated_at)
                    select gen_random_uuid(), work_order_id, 2, status, sent_at, sent_by, valid_until, 5000, 1150,
                           6150, repeat('b', 64), decided_at, decided_channel, decided_by, decided_name,
                           decided_note, 0, created_at, updated_at
                    from quote where work_order_id = ?::uuid""")) {
                s.setString(1, wo);
                return s.executeUpdate();
            }
        });
        moveOk(g.owner(), wo, "READY");
        moveOk(g.owner(), wo, "DELIVERED");

        report(g, "cost-vs-quote", "from=" + java.time.LocalDate.now(LISBON_ZONE).minusDays(1) + "&to="
                + java.time.LocalDate.now(LISBON_ZONE).plusDays(1)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].quote.number").value(2))
                .andExpect(jsonPath("$.items[0].quote.totalCents").value(6150))
                .andExpect(jsonPath("$.items[0].differenceCents").value(-1230));
    }

    @Test
    void should_not_count_a_rejected_or_superseded_quote_as_approved() throws Exception {
        Shop g = garage("Oficina Orcamento Recusado " + java.util.UUID.randomUUID());
        String wo = quotableOrder(g, false);
        String rejected = sendQuote(g, wo);
        publicCall(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post(publicQuote(g, tokenOfQuote(rejected)) + "/reject").content("{\"reason\":\"Caro\"}"))
                .andExpect(status().isOk());
        sendQuote(g, wo);
        labour(g, wo, "Extra", 1000, 23);
        deliver(g, wo);

        report(g, "cost-vs-quote", "from=" + java.time.LocalDate.now(LISBON_ZONE).minusDays(1) + "&to="
                + java.time.LocalDate.now(LISBON_ZONE).plusDays(1)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quote").doesNotExist())
                .andExpect(jsonPath("$.summary.ordersWithApprovedQuote").value(0));
    }

    @Test
    void should_show_margin_as_parts_only_when_the_labour_cost_is_not_set() throws Exception {
        Shop g = garage("Oficina Sem Custo " + java.util.UUID.randomUUID());
        String wo = quotableOrder(g, false);
        deliver(g, wo);
        entry(g, wo, mechanic(g).id(), null, java.time.Instant.now().minusSeconds(7200).toString(),
                java.time.Instant.now().minusSeconds(3600).toString());

        report(g, "cost-vs-quote", "from=" + java.time.LocalDate.now(LISBON_ZONE).minusDays(1) + "&to="
                + java.time.LocalDate.now(LISBON_ZONE).plusDays(1)).andExpect(status().isOk())
                .andExpect(jsonPath("$.labourCostSet").value(false))
                .andExpect(jsonPath("$.marginBasis").value("PARTS_ONLY"))
                .andExpect(jsonPath("$.summary.labourCostCents").doesNotExist())
                .andExpect(jsonPath("$.items[0].labourCostCents").doesNotExist())
                .andExpect(jsonPath("$.items[0].marginCents").value(4000))
                .andExpect(jsonPath("$.items[0].marginPercent").value(100.0));
    }

    @Test
    void should_count_a_labour_cost_of_zero_as_set() throws Exception {
        Shop g = garage("Oficina Custo Zero " + java.util.UUID.randomUUID());
        saveSettings(g, "PT", "Europe/Lisbon", "\"labourCostPerHourCents\":0");
        String wo = quotableOrder(g, false);
        deliver(g, wo);

        report(g, "cost-vs-quote", "from=" + java.time.LocalDate.now(LISBON_ZONE).minusDays(1) + "&to="
                + java.time.LocalDate.now(LISBON_ZONE).plusDays(1)).andExpect(status().isOk())
                .andExpect(jsonPath("$.labourCostSet").value(true))
                .andExpect(jsonPath("$.marginBasis").value("PARTS_AND_LABOUR"))
                .andExpect(jsonPath("$.items[0].labourCostCents").value(0));
    }

    private static final java.time.ZoneId LISBON_ZONE = java.time.ZoneId.of("Europe/Lisbon");
}

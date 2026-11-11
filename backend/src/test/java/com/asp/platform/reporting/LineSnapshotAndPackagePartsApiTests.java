package com.asp.platform.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;

/// A catalogue service line keeps the standard hours of the moment it was added (owner decision 2026-11-10); the lines of
/// a package keep the package they came from, so the parts sold through packages are told from the other parts.
class LineSnapshotAndPackagePartsApiTests extends ReportTestSupport {

    private record Row(String type, BigDecimal standardHours, UUID packageId) {
    }

    private List<Row> rows(Shop g, String wo) {
        return asWorkshop(g, c -> {
            try (PreparedStatement s = c.prepareStatement("""
                    select type, standard_hours, package_id from work_order_line
                    where work_order_id = ?::uuid order by position""")) {
                s.setString(1, wo);
                try (ResultSet rs = s.executeQuery()) {
                    List<Row> rows = new ArrayList<>();
                    while (rs.next()) {
                        rows.add(new Row(rs.getString(1), rs.getBigDecimal(2), rs.getObject(3, UUID.class)));
                    }
                    return rows;
                }
            }
        });
    }

    private static String today() {
        return LocalDate.now(ZoneId.of("Europe/Lisbon")).toString();
    }

    @Test
    void should_copy_the_standard_hours_onto_the_line_and_ignore_later_catalogue_changes() throws Exception {
        Shop g = garage("Oficina Snapshot " + UUID.randomUUID());
        String service = catalogueService(g, "Revisao", 4000, "1.5");
        String wo = workOrder(g, vehicle(g, false), null);
        addService(g, wo, service);
        labour(g, wo, "Escrita a mao", 1000, 23);

        List<Row> lines = rows(g, wo);
        assertThat(lines.get(0).standardHours()).isEqualByComparingTo("1.50");
        assertThat(lines.get(0).packageId()).isNull();
        assertThat(lines.get(1).standardHours()).as("a typed line has none").isNull();

        // The catalogue now says 9 hours: the order keeps the 1.5 it was sold with.
        call(put("/api/v1/catalogue/services/" + service).content(withVersion("""
                {"name":"Revisao","standardHours":9,"pricing":"FIXED","priceCents":4000,"vatRate":23,
                 "durationMinutes":60,"active":true,"listedOnline":false}""",
                versionAt(g.owner(), "/api/v1/catalogue/services/" + service))), g.owner())
                .andExpect(status().isOk());
        deliver(g, wo);

        report(g, "cost-vs-quote", "from=" + today() + "&to=" + today()).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].standardHours").value(1.5))
                .andExpect(jsonPath("$.summary.standardHours").value(1.5));
        // A line added after the change gets the new value.
        String second = workOrder(g, vehicle(g, false), null);
        addService(g, second, service);
        assertThat(rows(g, second).get(0).standardHours()).isEqualByComparingTo("9.00");
    }

    @Test
    void should_keep_standard_hours_and_package_on_the_lines_a_package_expands_into() throws Exception {
        Shop g = garage("Oficina Pacote " + UUID.randomUUID());
        String service = catalogueService(g, "Revisao", 4000, "1.5");
        String filter = product(g, "PKG-1", "Filtro", 1000, false, 10);
        String pack = idOf(call(post("/api/v1/catalogue/packages").content("""
                {"name":"Pacote","priceCents":9000,"vatRate":23,
                 "items":[{"serviceId":"%s","quantity":2},{"productId":"%s","quantity":1}]}"""
                .formatted(service, filter)), g.owner()).andExpect(status().isCreated()));
        String wo = workOrder(g, vehicle(g, false), null);
        String body = call(post("/api/v1/work-orders/" + wo + "/lines/from-catalogue")
                .content("{\"packageId\":\"%s\"}".formatted(pack)), g.owner()).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Object>>read(body, "$.lines")).hasSize(2);

        List<Row> lines = rows(g, wo);
        // The service line stands for two units of 1.5 h.
        assertThat(lines.get(0).type()).isEqualTo("LABOR");
        assertThat(lines.get(0).standardHours()).isEqualByComparingTo("3.00");
        assertThat(lines.get(0).packageId()).isEqualTo(UUID.fromString(pack));
        assertThat(lines.get(1).type()).isEqualTo("PART");
        assertThat(lines.get(1).standardHours()).isNull();
        assertThat(lines.get(1).packageId()).isEqualTo(UUID.fromString(pack));
    }

    @Test
    void should_report_the_parts_sold_through_packages_apart_from_the_other_parts() throws Exception {
        Shop g = garage("Oficina Pecas Pacote " + UUID.randomUUID());
        String service = catalogueService(g, "Revisao", 4000, "1.5");
        String filter = product(g, "PKG-2", "Filtro", 1000, false, 10);
        setProductCost(g, filter, 400);
        String pads = product(g, "PAD-2", "Pastilhas", 2000, false, 10);
        setProductCost(g, pads, 700);
        // List price 2 x 40,00 + 10,00 = 90,00 = the package price: every item keeps its own price.
        String pack = idOf(call(post("/api/v1/catalogue/packages").content("""
                {"name":"Pacote","priceCents":9000,"vatRate":23,
                 "items":[{"serviceId":"%s","quantity":2},{"productId":"%s","quantity":1}]}"""
                .formatted(service, filter)), g.owner()).andExpect(status().isCreated()));
        String wo = workOrder(g, vehicle(g, false), null);
        call(post("/api/v1/work-orders/" + wo + "/lines/from-catalogue")
                .content("{\"packageId\":\"%s\"}".formatted(pack)), g.owner()).andExpect(status().isCreated());
        addPart(g, wo, pads, 1);
        deliver(g, wo);

        // Net 8000 (service) + 1000 (package part) + 2000 (other part) = 11000; parts cost 400 + 700.
        report(g, "kpis", "from=" + today() + "&to=" + today()).andExpect(status().isOk())
                .andExpect(jsonPath("$.margin.netCents").value(11000))
                .andExpect(jsonPath("$.margin.partsCostCents").value(1100))
                .andExpect(jsonPath("$.margin.marginCents").value(9900))
                .andExpect(jsonPath("$.margin.packageParts.lines").value(1))
                .andExpect(jsonPath("$.margin.packageParts.netCents").value(1000))
                .andExpect(jsonPath("$.margin.packageParts.partsCostCents").value(400))
                .andExpect(jsonPath("$.margin.packageParts.marginCents").value(600))
                .andExpect(jsonPath("$.margin.packageParts.marginPercent").value(60.0))
                .andExpect(jsonPath("$.margin.otherParts.netCents").value(2000))
                .andExpect(jsonPath("$.margin.otherParts.partsCostCents").value(700))
                .andExpect(jsonPath("$.margin.otherParts.marginCents").value(1300))
                .andExpect(jsonPath("$.margin.otherParts.marginPercent").value(65.0))
                // The service of the package is a catalogue service like any other (labour only).
                .andExpect(jsonPath("$.margin.byService[0].netCents").value(8000));
    }

    @Test
    void should_report_no_package_parts_as_zeros_and_null_percent() throws Exception {
        Shop g = garage("Oficina Sem Pacote " + UUID.randomUUID());

        report(g, "kpis", "from=" + today() + "&to=" + today()).andExpect(status().isOk())
                .andExpect(jsonPath("$.margin.packageParts.lines").value(0))
                .andExpect(jsonPath("$.margin.packageParts.netCents").value(0))
                .andExpect(jsonPath("$.margin.packageParts.marginPercent").doesNotExist())
                .andExpect(jsonPath("$.margin.otherParts.netCents").value(0))
                .andExpect(jsonPath("$.margin.otherParts.marginPercent").doesNotExist());
    }
}

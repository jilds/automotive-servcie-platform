package com.asp.platform.reporting;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.test.web.servlet.ResultActions;

import com.asp.platform.billing.BillingTestSupport;
import com.jayway.jsonpath.JsonPath;

/// Delivered work orders with known hours, quotes, stock cost and invoices for the report tests. Work orders go
/// through the real API; their dates, and time entries that the API would refuse after delivery, are written as the
/// table owner (`asp_owner`, still under row-level security for the workshop).
///
/// [#seedScenario()] builds the fixed data that the plan 08 expectations are computed from. Lisbon is on UTC in
/// November, so the instants below are also the local times.
public abstract class ReportTestSupport extends BillingTestSupport {

    protected static final String PERIOD = "from=2026-11-01&to=2026-11-30";

    /// The fixed workshop (labour cost 30,00 EUR an hour, Lisbon):
    ///
    /// | order | mechanic | customer | vehicle | lines (net) | delivered (UTC) | tracked | invoice |
    /// |---|---|---|---|---|---|---|---|
    /// | o1 | Rui | C1 | V1 | service S1 4000 + 2 parts 2500 (cost 800 each) | 11-05 10:00 | 3.5 h (2 h on S1) | issued |
    /// | o2 | Rui | C2 | V2 | service S2 9000 | 11-12 16:00 | 2 h on S2 | issued, voided |
    /// | o3 | Ze | C1 | V3 | typed 5000 | 11-20 09:00 | 1 h | issued |
    /// | o4 | Teresa | C3 | V4 | typed 2000 | 11-25 12:00 | none | none |
    /// | o5 | Ze | C1 | V1 | typed 1000 | 11-22 10:00 | none | issued, credited |
    ///
    /// o1 had an approved quote of 4920 (S1 only) before the parts were added. Outside the period: o0 (C2, V2,
    /// delivered 2026-02-10) and o6 (C3, V4, delivered 2025-09-01). An open order o7 of Rui carries more entries
    /// around the period's edges and a running one for Teresa; o8 is cancelled.
    protected record Scenario(Shop shop, Mechanic rui, Mechanic teresa, Mechanic ze, String s1, String s2, String o0,
            String o1, String o2, String o3, String o4, String o5, String o6, String o7, String o8) {
    }

    protected Scenario seedScenario() throws Exception {
        Shop g = garage("Oficina Relatorios " + UUID.randomUUID());
        saveSettings(g, "PT", "Europe/Lisbon", "\"labourCostPerHourCents\":3000");
        Mechanic rui = mechanicNamed(g, "Rui");
        Mechanic teresa = mechanicNamed(g, "Teresa");
        Mechanic ze = mechanicNamed(g, "Ze");
        String s1 = catalogueService(g, "Mudanca oleo", 4000, "1.5");
        String s2 = catalogueService(g, "Travoes", 9000, "2");
        String part = product(g, "PAST-1", "Pastilhas", 1250, false, 10);
        setProductCost(g, part, 800);

        String c1 = createCustomer(g.owner(), "Cliente Um");
        String c2 = createCustomer(g.owner(), "Cliente Dois");
        String c3 = createCustomer(g.owner(), "Cliente Tres");
        String v1 = createVehicle(g.owner(), c1, "AA-10-AA");
        String v2 = createVehicle(g.owner(), c2, "AA-20-AA");
        String v3 = createVehicle(g.owner(), c1, "AA-30-AA");
        String v4 = createVehicle(g.owner(), c3, "AA-40-AA");

        // Outside the period: o0 (C2, nine months before) and o6 (C3, fourteen months before) are earlier orders, which do
        // not make a customer retained (retention looks forward); the C1 orders in the period follow each other.
        String o0 = workOrder(g, v2, null);
        labour(g, o0, "Revisao", 100, 23);
        deliver(g, o0);
        retime(g, o0, "2026-02-09T09:00:00Z", "2026-02-10T09:00:00Z");
        String o6 = workOrder(g, v4, null);
        labour(g, o6, "Revisao", 100, 23);
        deliver(g, o6);
        retime(g, o6, "2025-08-31T09:00:00Z", "2025-09-01T09:00:00Z");

        // o1: quote approved for the service alone, parts added afterwards.
        String o1 = workOrder(g, v1, rui.id());
        String o1Line = addService(g, o1, s1);
        approveQuote(g, o1);
        addPart(g, o1, part, 2);
        entry(g, o1, rui.id(), o1Line, "2026-11-04T07:00:00Z", "2026-11-04T09:00:00Z");
        entry(g, o1, rui.id(), null, "2026-11-04T10:00:00Z", "2026-11-04T11:30:00Z");
        moveOk(g.owner(), o1, "READY");
        moveOk(g.owner(), o1, "DELIVERED");
        retime(g, o1, "2026-11-04T06:00:00Z", "2026-11-05T10:00:00Z");
        issued(g, o1);

        String o2 = workOrder(g, v2, rui.id());
        String o2Line = addService(g, o2, s2);
        deliver(g, o2);
        entry(g, o2, rui.id(), o2Line, "2026-11-10T08:00:00Z", "2026-11-10T10:00:00Z");
        retime(g, o2, "2026-11-10T07:00:00Z", "2026-11-12T16:00:00Z");
        String invoice2 = issued(g, o2);
        call(post("/api/v1/invoices/" + invoice2 + "/void").content("{\"reason\":\"Engano\",\"version\":"
                + invoiceVersion(g, invoice2) + "}"), g.owner()).andExpect(status().isOk());

        String o3 = workOrder(g, v3, ze.id());
        labour(g, o3, "Diagnostico", 5000, 23);
        deliver(g, o3);
        entry(g, o3, ze.id(), null, "2026-11-20T08:00:00Z", "2026-11-20T09:00:00Z");
        retime(g, o3, "2026-11-20T08:00:00Z", "2026-11-20T09:00:00Z");
        issued(g, o3);

        String o4 = workOrder(g, v4, teresa.id());
        labour(g, o4, "Lavagem", 2000, 23);
        deliver(g, o4);
        retime(g, o4, "2026-11-25T08:00:00Z", "2026-11-25T12:00:00Z");

        String o5 = workOrder(g, v1, ze.id());
        labour(g, o5, "Polimento", 1000, 23);
        deliver(g, o5);
        retime(g, o5, "2026-11-20T08:00:00Z", "2026-11-22T10:00:00Z");
        String invoice5 = issued(g, o5);
        call(post("/api/v1/invoices/" + invoice5 + "/credit-notes")
                .content("{\"requestId\":\"%s\",\"reason\":\"Devolucao\"}".formatted(newRequestId())), g.owner())
                .andExpect(status().isCreated());

        // An open order with entries around the edges of the period, and a running timer.
        String o7 = workOrder(g, createVehicle(g.owner(), createCustomer(g.owner(), "Cliente Quatro"), "AA-70-AA"),
                rui.id());
        entry(g, o7, rui.id(), null, "2026-10-31T23:30:00Z", "2026-11-01T00:30:00Z");
        entry(g, o7, rui.id(), null, "2026-11-20T12:00:00Z", "2026-11-20T13:00:00Z");
        entry(g, o7, rui.id(), null, "2026-11-30T23:30:00Z", "2026-12-01T00:00:00Z");
        entry(g, o7, rui.id(), null, "2026-12-01T00:00:00Z", "2026-12-01T01:00:00Z");
        runningEntry(g, o7, teresa.id(), "2026-11-26T09:00:00Z");

        String o8 = workOrder(g, createVehicle(g.owner(), createCustomer(g.owner(), "Cliente Cinco"), "AA-80-AA"),
                ze.id());
        labour(g, o8, "Cancelado", 9999, 23);
        moveOk(g.owner(), o8, "CANCELLED");
        retime(g, o8, "2026-11-10T08:00:00Z", "2026-11-11T08:00:00Z");

        return new Scenario(g, rui, teresa, ze, s1, s2, o0, o1, o2, o3, o4, o5, o6, o7, o8);
    }

    // --- requests ---------------------------------------------------------------------------------------------

    protected ResultActions report(Shop g, String path, String query) throws Exception {
        return call(get("/api/v1/reports/" + path + (query.isEmpty() ? "" : "?" + query)), g.owner());
    }

    protected ResultActions report(String token, String path, String query) throws Exception {
        return call(get("/api/v1/reports/" + path + (query.isEmpty() ? "" : "?" + query)), token);
    }

    /// Changes the workshop settings; `extra` is more JSON members (or empty).
    protected void saveSettings(Shop g, String country, String timezone, String extra) throws Exception {
        String more = extra.isEmpty() ? "" : "," + extra;
        call(put("/api/v1/workshop/settings").content(withVersion("""
                {"name":"Oficina","country":"%s","timezone":"%s","defaultVatRate":23,"labourRateCents":0%s}"""
                .formatted(country, timezone, more), versionAt(g.owner(), "/api/v1/workshop/settings"))), g.owner())
                .andExpect(status().isOk());
    }

    // --- data -------------------------------------------------------------------------------------------------

    /// A mechanic whose name is `name` (the invite names every mechanic "mechanic").
    protected Mechanic mechanicNamed(Shop g, String name) throws Exception {
        Mechanic m = mechanic(g);
        renameUser(g, m.id(), name);
        return m;
    }

    protected void renameUser(Shop g, String userId, String name) {
        asOwner(g, c -> {
            try (PreparedStatement s = c.prepareStatement("update app_user set name = ? where id = ?::uuid")) {
                s.setString(1, name);
                s.setString(2, userId);
                return s.executeUpdate();
            }
        });
    }

    /// A fixed-price catalogue service with a standard time; returns its id.
    protected String catalogueService(Shop g, String name, long priceCents, String standardHours) throws Exception {
        return idOf(call(post("/api/v1/catalogue/services").content("""
                {"name":"%s","description":"Descricao","category":"Manutencao","standardHours":%s,"pricing":"FIXED",
                 "priceCents":%d,"vatRate":23,"durationMinutes":60,"active":true,"listedOnline":false}"""
                .formatted(name, standardHours, priceCents)), g.owner()).andExpect(status().isCreated()));
    }

    /// Adds the service to the order from the catalogue; returns the id of the new line.
    protected String addService(Shop g, String wo, String serviceId) throws Exception {
        String body = call(post("/api/v1/work-orders/" + wo + "/lines/from-catalogue")
                .content("{\"serviceId\":\"%s\"}".formatted(serviceId)), g.owner()).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        java.util.List<String> ids = JsonPath.read(body, "$.lines[?(@.serviceItemId=='" + serviceId + "')].id");
        return ids.getLast();
    }

    protected void addPart(Shop g, String wo, String productId, int quantity) throws Exception {
        call(post("/api/v1/work-orders/" + wo + "/lines/from-stock")
                .content("{\"productId\":\"%s\",\"quantity\":%d}".formatted(productId, quantity)), g.owner())
                .andExpect(status().isCreated());
    }

    protected void setProductCost(Shop g, String productId, long cents) {
        asOwner(g, c -> {
            try (PreparedStatement s = c.prepareStatement("update product set cost_cents = ? where id = ?::uuid")) {
                s.setLong(1, cents);
                s.setString(2, productId);
                return s.executeUpdate();
            }
        });
    }

    /// Sends a quote of the order's current lines and approves it on the customer's link.
    protected void approveQuote(Shop g, String wo) throws Exception {
        String sent = sendQuote(g, wo);
        approveOnLink(g, tokenOfQuote(sent)).andExpect(status().isOk());
    }

    /// Sets the check-in and delivery instants of an order (ISO instants).
    protected void retime(Shop g, String wo, String checkedIn, String deliveredAt) {
        int updated = asOwner(g, c -> {
            try (PreparedStatement s = c.prepareStatement(
                    "update work_order set created_at = ?, status_changed_at = ? where id = ?::uuid")) {
                s.setObject(1, at(checkedIn));
                s.setObject(2, at(deliveredAt));
                s.setString(3, wo);
                return s.executeUpdate();
            }
        });
        if (updated != 1) {
            throw new IllegalStateException("work order not found");
        }
    }

    /// A closed time entry (what a mechanic's start and stop would have recorded).
    protected void entry(Shop g, String wo, String mechanicId, @Nullable String lineId, String start, String stop) {
        insertEntry(g, wo, mechanicId, lineId, start, stop);
    }

    protected void runningEntry(Shop g, String wo, String mechanicId, String start) {
        insertEntry(g, wo, mechanicId, null, start, null);
    }

    private void insertEntry(Shop g, String wo, String mechanicId, @Nullable String lineId, String start,
            @Nullable String stop) {
        asOwner(g, c -> {
            try (PreparedStatement s = c.prepareStatement("""
                    insert into work_time_entry (id, work_order_id, line_id, mechanic_id, started_at, stopped_at,
                                                 version, created_at, updated_at)
                    values (?::uuid, ?::uuid, ?::uuid, ?::uuid, ?, ?, 0, now(), now())""")) {
                s.setString(1, UUID.randomUUID().toString());
                s.setString(2, wo);
                s.setString(3, lineId);
                s.setString(4, mechanicId);
                s.setObject(5, at(start));
                s.setObject(6, stop == null ? null : at(stop));
                return s.executeUpdate();
            }
        });
    }

    protected static OffsetDateTime at(String instant) {
        return OffsetDateTime.ofInstant(Instant.parse(instant), ZoneOffset.UTC);
    }
}

package com.asp.platform.workshop.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.YearMonth;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import com.asp.platform.ApiTestSupport;

class DashboardApiTests extends ApiTestSupport {

    @Test
    void should_show_the_owner_the_board_revenue_and_workload() throws Exception {
        String owner = signup("Oficina Painel");
        String rui = invite(owner, "MECHANIC", uniqueEmail("rui"));
        String ze = invite(owner, "MECHANIC", uniqueEmail("ze"));
        String retired = invite(owner, "MECHANIC", uniqueEmail("old"));
        call(put("/api/v1/users/" + retired).content(withVersion("{\"role\":\"MECHANIC\",\"active\":false}",
                userVersion(owner, retired))), owner)
                .andExpect(status().isOk());
        String customer = createCustomer(owner, "Frota Lda");

        // Delivered this month, with the same lines as the work-order totals test: 82.50 + 18.98 VAT.
        String delivered = checkIn(owner, createVehicle(owner, customer, "10-AA-01"), rui);
        addLine(owner, delivered, """
                {"type":"LABOR","description":"Pads","quantity":1.5,"unitPriceCents":4000,"vatRate":23}""");
        addLine(owner, delivered, """
                {"type":"PART","description":"Pads","quantity":2,"unitPriceCents":1250,"discountPercent":10,"vatRate":23}""");
        move(owner, delivered, "IN_PROGRESS");
        move(owner, delivered, "READY");
        move(owner, delivered, "DELIVERED");

        // Open: one in progress for Rui, one unassigned in diagnostics, one awaiting approval for Rui.
        move(owner, checkIn(owner, createVehicle(owner, customer, "10-AA-02"), rui), "IN_PROGRESS");
        checkIn(owner, createVehicle(owner, customer, "10-AA-03"), null);
        move(owner, checkIn(owner, createVehicle(owner, customer, "10-AA-04"), rui), "AWAITING_APPROVAL");
        // Cancelled orders are neither open nor delivered, and their lines are not revenue.
        String cancelled = checkIn(owner, createVehicle(owner, customer, "10-AA-05"), ze);
        addLine(owner, cancelled, """
                {"type":"PART","description":"Battery","quantity":1,"unitPriceCents":9000,"vatRate":23}""");
        move(owner, cancelled, "CANCELLED");

        String month = YearMonth.now(ZoneId.of("Europe/Lisbon")).toString();
        call(get("/api/v1/dashboard"), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value(month))
                .andExpect(jsonPath("$.openByStatus.length()").value(5))
                .andExpect(jsonPath("$.openByStatus.DIAGNOSTICS").value(1))
                .andExpect(jsonPath("$.openByStatus.AWAITING_APPROVAL").value(1))
                .andExpect(jsonPath("$.openByStatus.AWAITING_PARTS").value(0))
                .andExpect(jsonPath("$.openByStatus.IN_PROGRESS").value(1))
                .andExpect(jsonPath("$.openByStatus.READY").value(0))
                .andExpect(jsonPath("$.carsInShop").value(3))
                .andExpect(jsonPath("$.deliveredThisMonth").value(1))
                .andExpect(jsonPath("$.revenueThisMonth.netCents").value(8250))
                .andExpect(jsonPath("$.revenueThisMonth.vatCents").value(1898))
                .andExpect(jsonPath("$.revenueThisMonth.grossCents").value(10148))
                .andExpect(jsonPath("$.mechanicWorkload.length()").value(2))
                .andExpect(jsonPath("$.mechanicWorkload[?(@.mechanicId == '%s')].openOrders".formatted(rui)).value(2))
                .andExpect(jsonPath("$.mechanicWorkload[?(@.mechanicId == '%s')].openOrders".formatted(ze)).value(0))
                .andExpect(jsonPath("$.mechanicWorkload[?(@.mechanicId == '%s')].name".formatted(ze)).value("mechanic"));
    }

    @Test
    void should_show_an_empty_workshop_as_zeros() throws Exception {
        String owner = signup("Oficina Vazia");

        call(get("/api/v1/dashboard"), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openByStatus.DIAGNOSTICS").value(0))
                .andExpect(jsonPath("$.carsInShop").value(0))
                .andExpect(jsonPath("$.deliveredThisMonth").value(0))
                .andExpect(jsonPath("$.revenueThisMonth.grossCents").value(0))
                .andExpect(jsonPath("$.mechanicWorkload.length()").value(0));
    }

    @Test
    void should_keep_the_dashboard_for_the_owner_and_each_workshop_to_itself() throws Exception {
        String owner = signup("Oficina Privada");
        String deskEmail = uniqueEmail("desk");
        invite(owner, "FRONT_DESK", deskEmail);
        String mechanicEmail = uniqueEmail("mec");
        invite(owner, "MECHANIC", mechanicEmail);
        String other = signup("Oficina Ocupada");
        checkIn(other, createVehicle(other, createCustomer(other, "Outro"), "20-BB-01"), null);

        call(get("/api/v1/dashboard"), login(deskEmail, PASSWORD)).andExpect(status().isForbidden());
        call(get("/api/v1/dashboard"), login(mechanicEmail, PASSWORD)).andExpect(status().isForbidden());
        call(get("/api/v1/dashboard"), owner).andExpect(jsonPath("$.carsInShop").value(0));
        call(get("/api/v1/dashboard"), other).andExpect(jsonPath("$.carsInShop").value(1));
    }

    private String checkIn(String token, String vehicleId, String mechanicId) throws Exception {
        String mechanic = mechanicId == null ? "null" : "\"" + mechanicId + "\"";
        return idOf(call(post("/api/v1/work-orders").content("""
                {"vehicleId":"%s","assignedMechanicId":%s}""".formatted(vehicleId, mechanic)), token)
                .andExpect(status().isCreated()));
    }

    private void addLine(String token, String workOrderId, String line) throws Exception {
        call(post("/api/v1/work-orders/" + workOrderId + "/lines").content(line), token)
                .andExpect(status().isCreated());
    }

    private void move(String token, String workOrderId, String target) throws Exception {
        call(patch("/api/v1/work-orders/" + workOrderId + "/status").content("{\"status\":\"%s\"}".formatted(target)),
                token).andExpect(status().isOk());
    }
}

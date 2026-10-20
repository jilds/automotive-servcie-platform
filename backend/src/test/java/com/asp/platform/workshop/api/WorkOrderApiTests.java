package com.asp.platform.workshop.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.asp.platform.ApiTestSupport;
import com.jayway.jsonpath.JsonPath;

class WorkOrderApiTests extends ApiTestSupport {

    @Test
    void should_run_a_work_order_through_the_board_with_totals_and_role_rules() throws Exception {
        String owner = signup("Oficina Central");
        String vehicleId = createVehicle(owner, createCustomer(owner, "Ana Pereira"), "12-AB-34");

        String mechanicEmail = uniqueEmail("mec");
        String mechanicId = idOf(call(post("/api/v1/users").content("""
                {"name":"Rui","email":"%s","role":"MECHANIC","temporaryPassword":"mechanic123"}"""
                .formatted(mechanicEmail)), owner).andExpect(status().isCreated()));

        String woId = idOf(call(post("/api/v1/work-orders").content("""
                {"vehicleId":"%s","mileageIn":123456,"fuelLevel":4,"complaint":"Brake noise","assignedMechanicId":"%s"}"""
                .formatted(vehicleId, mechanicId)), owner)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.status").value("DIAGNOSTICS")));

        call(post("/api/v1/work-orders/" + woId + "/lines").content("""
                {"type":"LABOR","description":"Replace front pads","quantity":1.5,"unitPriceCents":4000,"vatRate":23}"""),
                owner).andExpect(status().isCreated());
        call(post("/api/v1/work-orders/" + woId + "/lines").content("""
                {"type":"PART","description":"Brake pads","quantity":2,"unitPriceCents":1250,"discountPercent":10,"vatRate":23}"""),
                owner)
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andExpect(jsonPath("$.netCents").value(8250))
                .andExpect(jsonPath("$.vatCents").value(1898))
                .andExpect(jsonPath("$.totalCents").value(10148));

        call(get("/api/v1/vehicles/" + vehicleId), owner).andExpect(jsonPath("$.mileage").value(123456));

        // The owner cannot skip straight to delivered.
        call(patch("/api/v1/work-orders/" + woId + "/status").content("{\"status\":\"DELIVERED\"}"), owner)
                .andExpect(status().isConflict());

        String mechanic = login(mechanicEmail, "mechanic123");
        call(get("/api/v1/work-orders/" + woId), mechanic)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].unitPriceCents").doesNotExist())
                .andExpect(jsonPath("$.totalCents").doesNotExist());
        call(get("/api/v1/customers"), mechanic).andExpect(status().isForbidden());
        call(patch("/api/v1/work-orders/" + woId + "/status").content("{\"status\":\"CANCELLED\"}"), mechanic)
                .andExpect(status().isForbidden());
        call(patch("/api/v1/work-orders/" + woId + "/status").content("{\"status\":\"IN_PROGRESS\"}"), mechanic)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        call(get("/api/v1/work-orders/board"), mechanic)
                .andExpect(jsonPath("$.columns.IN_PROGRESS[0].plate").value("12AB34"))
                .andExpect(jsonPath("$.columns.IN_PROGRESS[0].vehicle").value("Renault Clio"));

        // Unassigned: the mechanic no longer sees it.
        call(put("/api/v1/work-orders/" + woId + "/mechanic").content("{}"), owner).andExpect(status().isOk());
        call(get("/api/v1/work-orders/" + woId), mechanic).andExpect(status().isNotFound());

        call(get("/api/v1/vehicles/" + vehicleId + "/work-orders"), owner)
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void should_remove_a_line_and_lock_lines_once_ready() throws Exception {
        String owner = signup("Oficina Linhas");
        String vehicleId = createVehicle(owner, createCustomer(owner, "Luís"), "34-CD-56");
        String woId = idOf(call(post("/api/v1/work-orders").content("{\"vehicleId\":\"%s\"}".formatted(vehicleId)),
                owner));
        String json = call(post("/api/v1/work-orders/" + woId + "/lines").content("""
                {"type":"LABOR","description":"Oil change","quantity":1,"unitPriceCents":3000,"vatRate":23}"""),
                owner).andReturn().getResponse().getContentAsString();
        String lineId = JsonPath.read(json, "$.lines[0].id");

        call(delete("/api/v1/work-orders/" + woId + "/lines/" + lineId), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines.length()").value(0))
                .andExpect(jsonPath("$.totalCents").value(0));

        call(patch("/api/v1/work-orders/" + woId + "/status").content("{\"status\":\"IN_PROGRESS\"}"), owner);
        call(patch("/api/v1/work-orders/" + woId + "/status").content("{\"status\":\"READY\"}"), owner)
                .andExpect(status().isOk());
        call(post("/api/v1/work-orders/" + woId + "/lines").content("""
                {"type":"PART","description":"Filter","quantity":1,"unitPriceCents":900,"vatRate":23}"""), owner)
                .andExpect(status().isConflict());
    }

    @Test
    void should_return_404_when_checking_in_an_unknown_vehicle() throws Exception {
        String owner = signup("Oficina 404");
        call(post("/api/v1/work-orders").content("{\"vehicleId\":\"00000000-0000-0000-0000-000000000000\"}"), owner)
                .andExpect(status().isNotFound());
    }
}

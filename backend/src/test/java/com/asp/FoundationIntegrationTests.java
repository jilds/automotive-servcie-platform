package com.asp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

@AutoConfigureMockMvc
class FoundationIntegrationTests extends PostgresTestBase {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void workshopsNeverSeeEachOthersData() throws Exception {
        String a = signup("Oficina A");
        String b = signup("Oficina B");

        String customerId = createCustomer(a, "Maria Silva");
        String vehicleId = createVehicle(a, customerId, "AA-11-BB");

        call(get("/api/v1/customers"), b).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        call(get("/api/v1/vehicles/" + vehicleId), b).andExpect(status().isNotFound());
        call(get("/api/v1/vehicles?plate=AA11BB"), b).andExpect(status().isNotFound());
        call(get("/api/v1/vehicles?plate=aa 11 bb"), a).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(vehicleId));

        // Same plate is allowed in another workshop.
        String otherCustomer = createCustomer(b, "João Costa");
        createVehicle(b, otherCustomer, "AA-11-BB");

        // Straight to the database with no tenant set: row-level security hides everything.
        assertThat(jdbc.queryForObject("select count(*) from customer", Integer.class)).isZero();
    }

    @Test
    void workOrderRunsThroughTheBoardWithTotalsAndRoleRules() throws Exception {
        String owner = signup("Oficina Central");
        String customerId = createCustomer(owner, "Ana Pereira");
        String vehicleId = createVehicle(owner, customerId, "12-AB-34");

        String mechanicEmail = "mec-" + UUID.randomUUID() + "@test.pt";
        String mechanicId = JsonPath.read(call(post("/api/v1/users").content("""
                {"name":"Rui","email":"%s","role":"MECHANIC","temporaryPassword":"mechanic123"}"""
                .formatted(mechanicEmail)), owner).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString(), "$.id");

        String wo = call(post("/api/v1/work-orders").content("""
                {"vehicleId":"%s","mileageIn":123456,"fuelLevel":4,"complaint":"Brake noise","assignedMechanicId":"%s"}"""
                .formatted(vehicleId, mechanicId)), owner)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.status").value("DIAGNOSTICS"))
                .andReturn().getResponse().getContentAsString();
        String woId = JsonPath.read(wo, "$.id");

        call(post("/api/v1/work-orders/" + woId + "/lines").content("""
                {"type":"LABOR","description":"Replace front pads","quantity":1.5,"unitPriceCents":4000,"vatRate":23}"""),
                owner).andExpect(status().isCreated());
        call(post("/api/v1/work-orders/" + woId + "/lines").content("""
                {"type":"PART","description":"Brake pads","quantity":2,"unitPriceCents":1250,"discountPercent":10,"vatRate":23}"""),
                owner)
                .andExpect(jsonPath("$.netCents").value(8250))
                .andExpect(jsonPath("$.vatCents").value(1898))
                .andExpect(jsonPath("$.totalCents").value(10148));

        call(get("/api/v1/vehicles/" + vehicleId), owner).andExpect(jsonPath("$.mileage").value(123456));

        // Owner cannot skip straight to delivered.
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
                .andExpect(jsonPath("$.columns.IN_PROGRESS[0].plate").value("12AB34"));

        // Unassign: the mechanic no longer sees it.
        call(put("/api/v1/work-orders/" + woId + "/mechanic").content("{}"), owner).andExpect(status().isOk());
        call(get("/api/v1/work-orders/" + woId), mechanic).andExpect(status().isNotFound());

        call(get("/api/v1/vehicles/" + vehicleId + "/work-orders"), owner)
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void apiRequiresALogin() throws Exception {
        mvc.perform(get("/api/v1/customers")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nobody@test.pt\",\"password\":\"wrong-pass\"}"))
                .andExpect(status().isUnauthorized());
    }

    private String signup(String workshop) throws Exception {
        String body = """
                {"workshopName":"%s","ownerName":"Owner","email":"%s","password":"password123"}"""
                .formatted(workshop, "owner-" + UUID.randomUUID() + "@test.pt");
        String json = mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.accessToken");
    }

    private String login(String email, String password) throws Exception {
        String json = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.accessToken");
    }

    private String createCustomer(String token, String name) throws Exception {
        String json = call(post("/api/v1/customers").content("""
                {"name":"%s","nif":"123456789","phone":"+351912345678"}""".formatted(name)), token)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    private String createVehicle(String token, String customerId, String plate) throws Exception {
        String json = call(post("/api/v1/vehicles").content("""
                {"customerId":"%s","plate":"%s","make":"Renault","model":"Clio","year":2019,"mileage":100000}"""
                .formatted(customerId, plate)), token)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    private ResultActions call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req,
            String token) throws Exception {
        return mvc.perform(req.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON));
    }
}

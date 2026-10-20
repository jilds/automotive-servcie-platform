package com.asp.platform.customer.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.asp.platform.ApiTestSupport;

class CustomerApiTests extends ApiTestSupport {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void should_never_show_one_workshop_the_data_of_another() throws Exception {
        String a = signup("Oficina A");
        String b = signup("Oficina B");

        String customerId = createCustomer(a, "Maria Silva");
        String vehicleId = createVehicle(a, customerId, "AA-11-BB");

        call(get("/api/v1/customers"), b).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        call(get("/api/v1/customers/" + customerId), b).andExpect(status().isNotFound());
        call(get("/api/v1/vehicles/" + vehicleId), b).andExpect(status().isNotFound());
        call(get("/api/v1/vehicles?plate=AA11BB"), b).andExpect(status().isNotFound());
        call(get("/api/v1/vehicles?plate=aa 11 bb"), a).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(vehicleId));

        // The same plate is allowed in another workshop.
        createVehicle(b, createCustomer(b, "João Costa"), "AA-11-BB");

        // Straight to the database with no workshop bound: row-level security hides everything.
        assertThat(jdbc.queryForObject("select count(*) from customer", Integer.class)).isZero();
    }

    @Test
    void should_store_a_structured_address_and_validate_it() throws Exception {
        String owner = signup("Oficina Morada");
        call(post("/api/v1/customers").content("""
                {"name":"Carla","address":{"street":"Rua Augusta, 120","detail":"2º Esq.","postalCode":"1100-053","locality":"Lisboa"}}"""),
                owner)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.address.street").value("Rua Augusta, 120"))
                .andExpect(jsonPath("$.address.detail").value("2º Esq."))
                .andExpect(jsonPath("$.address.postalCode").value("1100-053"))
                .andExpect(jsonPath("$.address.locality").value("Lisboa"))
                .andExpect(jsonPath("$.address.country").value("PT"));

        call(post("/api/v1/customers").content("{\"name\":\"Sem morada\"}"), owner)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.address").doesNotExist());

        call(post("/api/v1/customers").content("""
                {"name":"Incompleta","address":{"street":"Rua Augusta, 120"}}"""), owner)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['address.postalCode']").exists())
                .andExpect(jsonPath("$.errors['address.locality']").exists());

        call(post("/api/v1/customers").content("""
                {"name":"Errada","address":{"street":"Rua A","postalCode":"1100","locality":"Lisboa"}}"""), owner)
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_reject_a_duplicate_plate_in_the_same_workshop() throws Exception {
        String owner = signup("Oficina Dup");
        String customerId = createCustomer(owner, "Rita");
        createVehicle(owner, customerId, "99-ZZ-99");
        call(post("/api/v1/vehicles").content("""
                {"customerId":"%s","plate":"99 zz 99"}""".formatted(customerId)), owner)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("duplicate-plate"));
    }
}

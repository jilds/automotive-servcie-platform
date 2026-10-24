package com.asp.platform.customer.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
        call(get("/api/v1/vehicles?plate=AA11BB"), b).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("vehicle-not-found"));
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
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation"))
                .andExpect(jsonPath("$.errors['address.postalCode']").exists());
        // Explicitly Portuguese too; a foreign address keeps its own format.
        call(post("/api/v1/customers").content("""
                {"name":"Errada PT","address":{"street":"Rua A","postalCode":"1100053","locality":"Lisboa","country":"pt"}}"""),
                owner)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['address.postalCode']").exists());
        call(post("/api/v1/customers").content("""
                {"name":"Iker","address":{"street":"Calle Mayor 1","postalCode":"28013","locality":"Madrid","country":"ES"}}"""),
                owner)
                .andExpect(status().isCreated());
    }

    @Test
    void should_edit_a_vehicle_and_keep_plates_unique_and_mileage_rising() throws Exception {
        String owner = signup("Oficina Editar");
        String customerId = createCustomer(owner, "Paulo");
        String vehicleId = createVehicle(owner, customerId, "11-AA-11");
        createVehicle(owner, customerId, "22-BB-22");

        call(put("/api/v1/vehicles/" + vehicleId).content(withVersion("""
                {"plate":"11-aa-11","vin":"vf1abcdefgh123456","make":"Renault","model":"Mégane","year":2020,
                 "fuelType":"Diesel","mileage":100500}""", versionAt(owner, "/api/v1/vehicles/" + vehicleId))), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(vehicleId))
                .andExpect(jsonPath("$.customerId").value(customerId))
                .andExpect(jsonPath("$.plate").value("11AA11"))
                .andExpect(jsonPath("$.vin").value("VF1ABCDEFGH123456"))
                .andExpect(jsonPath("$.model").value("Mégane"))
                .andExpect(jsonPath("$.fuelType").value("Diesel"))
                .andExpect(jsonPath("$.mileage").value(100500));

        // No mileage keeps the recorded reading.
        call(put("/api/v1/vehicles/" + vehicleId).content(withVersion("{\"plate\":\"33-CC-33\"}",
                versionAt(owner, "/api/v1/vehicles/" + vehicleId))), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plate").value("33CC33"))
                .andExpect(jsonPath("$.make").doesNotExist())
                .andExpect(jsonPath("$.mileage").value(100500));

        call(put("/api/v1/vehicles/" + vehicleId).content(withVersion("{\"plate\":\"22 bb 22\"}",
                versionAt(owner, "/api/v1/vehicles/" + vehicleId))), owner)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/duplicate-plate"))
                .andExpect(jsonPath("$.code").value("duplicate-plate"));

        call(put("/api/v1/vehicles/" + vehicleId).content(withVersion("{\"plate\":\"33CC33\",\"mileage\":100499}",
                versionAt(owner, "/api/v1/vehicles/" + vehicleId))), owner)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/mileage-decrease"))
                .andExpect(jsonPath("$.code").value("mileage-decrease"));

        call(put("/api/v1/vehicles/" + vehicleId).content(withVersion("{\"plate\":\"\",\"year\":1900,\"mileage\":-1}",
                versionAt(owner, "/api/v1/vehicles/" + vehicleId))), owner)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.plate").exists())
                .andExpect(jsonPath("$.errors.year").exists())
                .andExpect(jsonPath("$.errors.mileage").exists());

        call(put("/api/v1/vehicles/00000000-0000-0000-0000-000000000000").content("{\"plate\":\"44DD44\",\"version\":0}"), owner)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("vehicle-not-found"));

        String mechanicEmail = uniqueEmail("mec");
        invite(owner, "MECHANIC", mechanicEmail);
        String mechanic = login(mechanicEmail, PASSWORD);
        call(put("/api/v1/vehicles/" + vehicleId).content(withVersion("{\"plate\":\"55EE55\"}",
                versionAt(mechanic, "/api/v1/vehicles/" + vehicleId))), mechanic)
                .andExpect(status().isForbidden());
    }

    @Test
    void should_transfer_a_vehicle_with_its_work_order_history() throws Exception {
        String owner = signup("Oficina Venda");
        String seller = createCustomer(owner, "Vendedor");
        String buyer = createCustomer(owner, "Comprador");
        String vehicleId = createVehicle(owner, seller, "66-FF-66");
        call(post("/api/v1/work-orders").content("{\"vehicleId\":\"%s\"}".formatted(vehicleId)), owner)
                .andExpect(status().isCreated());

        call(put("/api/v1/vehicles/" + vehicleId + "/owner").content(withVersion("{\"customerId\":\"%s\"}".formatted(buyer),
                versionAt(owner, "/api/v1/vehicles/" + vehicleId))),
                owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(buyer))
                .andExpect(jsonPath("$.plate").value("66FF66"));

        call(get("/api/v1/customers/" + buyer + "/vehicles"), owner).andExpect(jsonPath("$.length()").value(1));
        call(get("/api/v1/customers/" + seller + "/vehicles"), owner).andExpect(jsonPath("$.length()").value(0));
        call(get("/api/v1/vehicles/" + vehicleId + "/work-orders"), owner).andExpect(jsonPath("$.length()").value(1));

        call(put("/api/v1/vehicles/" + vehicleId + "/owner")
                .content(withVersion("{\"customerId\":\"00000000-0000-0000-0000-000000000000\"}",
                        versionAt(owner, "/api/v1/vehicles/" + vehicleId))), owner)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("customer-not-found"));
        call(put("/api/v1/vehicles/" + vehicleId + "/owner").content(withVersion("{}",
                versionAt(owner, "/api/v1/vehicles/" + vehicleId))), owner)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.customerId").exists());
    }

    @Test
    void should_not_let_another_workshop_edit_or_take_a_vehicle() throws Exception {
        String a = signup("Oficina Dona");
        String b = signup("Oficina Intrusa");
        String customerA = createCustomer(a, "Cliente A");
        String vehicleA = createVehicle(a, customerA, "77-GG-77");
        String customerB = createCustomer(b, "Cliente B");
        String vehicleB = createVehicle(b, customerB, "88-HH-88");

        call(put("/api/v1/vehicles/" + vehicleA).content(withVersion("{\"plate\":\"77GG78\"}",
                versionAt(b, "/api/v1/vehicles/" + vehicleA))), b)
                .andExpect(status().isNotFound());
        call(put("/api/v1/vehicles/" + vehicleA + "/owner").content(withVersion("{\"customerId\":\"%s\"}".formatted(customerB),
                versionAt(b, "/api/v1/vehicles/" + vehicleA))),
                b).andExpect(status().isNotFound());
        // Nor hand one's own vehicle to another workshop's customer.
        call(put("/api/v1/vehicles/" + vehicleB + "/owner").content(withVersion("{\"customerId\":\"%s\"}".formatted(customerA),
                versionAt(b, "/api/v1/vehicles/" + vehicleB))),
                b).andExpect(status().isNotFound());

        call(get("/api/v1/vehicles/" + vehicleA), a)
                .andExpect(jsonPath("$.plate").value("77GG77"))
                .andExpect(jsonPath("$.customerId").value(customerA));
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

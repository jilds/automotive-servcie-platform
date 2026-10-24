package com.asp.platform.customer.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.asp.platform.ApiTestSupport;

/// Release 3 rules on customers and vehicles: `PUT /customers/{id}`, optimistic locking (`version`, 409
/// `stale-data`), the Portuguese NIF check digit and look-up by VIN.
class CustomerEditApiTests extends ApiTestSupport {

    /// Valid Portuguese NIFs (mod-11 check digit and known first digits).
    private static final String VALID_PERSON_NIF = "123456789";
    private static final String VALID_COMPANY_NIF = "509999999";
    /// Same digits as a valid NIF with the last one changed: wrong check digit.
    private static final String WRONG_CHECK_DIGIT = "123456780";
    /// Valid check digit, but no NIF starts with 4 followed by 0.
    private static final String WRONG_FIRST_DIGITS = "400000006";

    @Test
    void should_edit_a_customer_with_the_version_it_read() throws Exception {
        String owner = signup("Oficina Edita Cliente");
        String id = createCustomer(owner, "Rita");

        call(put("/api/v1/customers/" + id).content("""
                {"name":"Rita Sousa","nif":"%s","phone":"+351911111111","email":"rita@cliente.pt",
                 "address":{"street":"Rua Augusta, 120","postalCode":"1100-053","locality":"Lisboa"},
                 "marketingConsent":true,"version":0}""".formatted(VALID_COMPANY_NIF)), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Rita Sousa"))
                .andExpect(jsonPath("$.nif").value(VALID_COMPANY_NIF))
                .andExpect(jsonPath("$.address.postalCode").value("1100-053"))
                .andExpect(jsonPath("$.marketingConsent").value(true))
                .andExpect(jsonPath("$.version").value(1));

        call(get("/api/v1/customers/" + id), owner)
                .andExpect(jsonPath("$.name").value("Rita Sousa"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void should_refuse_an_edit_based_on_an_old_version_with_stale_data() throws Exception {
        String owner = signup("Oficina Versao Cliente");
        String id = createCustomer(owner, "Duarte");
        call(put("/api/v1/customers/" + id).content("{\"name\":\"Duarte A\",\"version\":0}"), owner)
                .andExpect(status().isOk());

        // A second desk still has version 0 open: its save must not overwrite the first one.
        call(put("/api/v1/customers/" + id).content("{\"name\":\"Duarte B\",\"version\":0}"), owner)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/stale-data"))
                .andExpect(jsonPath("$.code").value("stale-data"));
        call(get("/api/v1/customers/" + id), owner).andExpect(jsonPath("$.name").value("Duarte A"));

        call(put("/api/v1/customers/" + id).content("{\"name\":\"Duarte C\"}"), owner)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation"))
                .andExpect(jsonPath("$.errors.version").exists());
    }

    @Test
    void should_validate_an_edited_customer() throws Exception {
        String owner = signup("Oficina Valida Cliente");
        String id = createCustomer(owner, "Sofia");

        call(put("/api/v1/customers/" + id).content("""
                {"name":" ","nif":"12","email":"not-an-email",
                 "address":{"street":"Rua A","postalCode":"1100","locality":"Lisboa"},"version":0}"""), owner)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation"))
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.nif").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors['address.postalCode']").exists());

        call(put("/api/v1/customers/00000000-0000-0000-0000-000000000000").content(
                "{\"name\":\"Ninguém\",\"version\":0}"), owner)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("customer-not-found"));
    }

    @Test
    void should_not_let_another_workshop_edit_a_customer() throws Exception {
        String a = signup("Oficina Cliente Dono");
        String b = signup("Oficina Cliente Intrusa");
        String id = createCustomer(a, "Helena");

        call(put("/api/v1/customers/" + id).content("{\"name\":\"Hacked\",\"version\":0}"), b)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("customer-not-found"));
        call(get("/api/v1/customers/" + id), a)
                .andExpect(jsonPath("$.name").value("Helena"))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void should_keep_mechanics_out_of_customer_edits() throws Exception {
        String owner = signup("Oficina Cliente Mecanico");
        String id = createCustomer(owner, "Bruno");
        String email = uniqueEmail("mec");
        invite(owner, "MECHANIC", email);

        call(put("/api/v1/customers/" + id).content("{\"name\":\"Bruno\",\"version\":0}"), login(email, PASSWORD))
                .andExpect(status().isForbidden());
    }

    @Test
    void should_check_the_nif_of_a_customer_in_portugal() throws Exception {
        String owner = signup("Oficina NIF");

        // With a Portuguese address the API checks it: a validation error on the field.
        call(post("/api/v1/customers").content("""
                {"name":"Errado","nif":"%s",
                 "address":{"street":"Rua A","postalCode":"1100-053","locality":"Lisboa"}}"""
                .formatted(WRONG_CHECK_DIGIT)), owner)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation"))
                .andExpect(jsonPath("$.errors.nif").exists());
        // Without an address, the workshop's country (PT) applies: the domain refuses it with its own code.
        call(post("/api/v1/customers").content("{\"name\":\"Errado\",\"nif\":\"%s\"}".formatted(WRONG_FIRST_DIGITS)),
                owner)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/invalid-nif"))
                .andExpect(jsonPath("$.code").value("invalid-nif"))
                .andExpect(jsonPath("$.errors.nif").exists());

        call(post("/api/v1/customers").content("{\"name\":\"Certo\",\"nif\":\"%s\"}".formatted(VALID_PERSON_NIF)),
                owner)
                .andExpect(status().isCreated());
        // A customer living abroad keeps the tax number they gave.
        call(post("/api/v1/customers").content("""
                {"name":"Iker","nif":"%s",
                 "address":{"street":"Calle Mayor 1","postalCode":"28013","locality":"Madrid","country":"ES"}}"""
                .formatted(WRONG_CHECK_DIGIT)), owner)
                .andExpect(status().isCreated());
    }

    @Test
    void should_check_a_stored_invalid_nif_only_when_the_customer_is_edited() throws Exception {
        String owner = signup("Oficina NIF Antigo");
        // Registered with a Spanish address, so the NIF was not checked; moving the customer to Portugal
        // without fixing the NIF is refused.
        String id = idOf(call(post("/api/v1/customers").content("""
                {"name":"Ana","nif":"%s",
                 "address":{"street":"Calle Mayor 1","postalCode":"28013","locality":"Madrid","country":"ES"}}"""
                .formatted(WRONG_CHECK_DIGIT)), owner).andExpect(status().isCreated()));
        call(get("/api/v1/customers/" + id), owner).andExpect(status().isOk());

        call(put("/api/v1/customers/" + id).content("""
                {"name":"Ana","nif":"%s","version":0}""".formatted(WRONG_CHECK_DIGIT)), owner)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid-nif"));
        call(put("/api/v1/customers/" + id).content("""
                {"name":"Ana","nif":"%s","version":0}""".formatted(VALID_PERSON_NIF)), owner)
                .andExpect(status().isOk());
    }

    @Test
    void should_refuse_stale_vehicle_edits_and_transfers() throws Exception {
        String owner = signup("Oficina Versao Viatura");
        String customer = createCustomer(owner, "Pedro");
        String buyer = createCustomer(owner, "Joana");
        String vehicleId = createVehicle(owner, customer, "21-AB-43");
        call(get("/api/v1/vehicles/" + vehicleId), owner).andExpect(jsonPath("$.version").value(0));

        // A check-in raises the mileage: the vehicle changed, so a form opened before it is stale.
        call(post("/api/v1/work-orders").content("""
                {"vehicleId":"%s","mileageIn":120000}""".formatted(vehicleId)), owner)
                .andExpect(status().isCreated());

        call(put("/api/v1/vehicles/" + vehicleId).content("{\"plate\":\"21-AB-43\",\"make\":\"Seat\",\"version\":0}"),
                owner)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("stale-data"));
        call(put("/api/v1/vehicles/" + vehicleId + "/owner").content("""
                {"customerId":"%s","version":0}""".formatted(buyer)), owner)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("stale-data"));

        call(put("/api/v1/vehicles/" + vehicleId).content("{\"plate\":\"21-AB-43\",\"make\":\"Seat\",\"version\":1}"),
                owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.make").value("Seat"))
                .andExpect(jsonPath("$.mileage").value(120000))
                .andExpect(jsonPath("$.version").value(2));
        call(put("/api/v1/vehicles/" + vehicleId + "/owner").content("""
                {"customerId":"%s","version":2}""".formatted(buyer)), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(buyer))
                .andExpect(jsonPath("$.version").value(3));
    }

    @Test
    void should_find_a_vehicle_by_vin_in_its_own_workshop_only() throws Exception {
        String a = signup("Oficina VIN A");
        String b = signup("Oficina VIN B");
        String vehicleId = idOf(call(post("/api/v1/vehicles").content("""
                {"customerId":"%s","plate":"45-CD-67","vin":"vf1rja00x12345678"}"""
                .formatted(createCustomer(a, "Vasco"))), a).andExpect(status().isCreated()));
        String email = uniqueEmail("mec");
        invite(a, "MECHANIC", email);

        call(get("/api/v1/vehicles").param("vin", "VF1RJA00X12345678"), a)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(vehicleId))
                .andExpect(jsonPath("$.vin").value("VF1RJA00X12345678"));
        // Any case, spaces between groups, and mechanics may look it up too.
        call(get("/api/v1/vehicles").param("vin", " vf1 rja00x 12345678 "), login(email, PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(vehicleId));

        call(get("/api/v1/vehicles").param("vin", "VF1RJA00X12345678"), b)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("vehicle-not-found"));
        call(get("/api/v1/vehicles").param("vin", "VF1RJA00X00000000"), a)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("vehicle-not-found"));
        // Exact match only: a part of a VIN is refused rather than searched.
        call(get("/api/v1/vehicles").param("vin", "VF1RJA00X"), a)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid-vin"))
                .andExpect(jsonPath("$.errors.vin").exists());
    }
}

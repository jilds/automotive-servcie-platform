package com.asp.platform.workshop.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.asp.platform.ApiTestSupport;

/// Changing the mechanic of a work order: a reason is required to replace or remove one, every change is kept with
/// who made it and why, and only the owner and the front desk of the same workshop may do it.
class MechanicChangeApiTests extends ApiTestSupport {

    @Test
    void should_require_a_reason_to_replace_a_mechanic_and_keep_the_history() throws Exception {
        String owner = signup("Oficina Troca");
        String deskEmail = uniqueEmail("desk");
        invite(owner, "FRONT_DESK", deskEmail);
        String desk = login(deskEmail, PASSWORD);
        String rui = invite(owner, "MECHANIC", uniqueEmail("rui"));
        String ze = invite(owner, "MECHANIC", uniqueEmail("ze"));
        String woId = openWorkOrder(owner, "44-AB-55");

        // First assignment: no reason needed.
        assign(desk, woId, "{\"mechanicId\":\"%s\"}".formatted(rui))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedMechanicId").value(rui));

        // Replacing him needs a reason.
        assign(desk, woId, "{\"mechanicId\":\"%s\",\"reason\":\"  \"}".formatted(ze))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/reason-required"))
                .andExpect(jsonPath("$.code").value("reason-required"))
                .andExpect(jsonPath("$.errors.reason").exists());
        assign(desk, woId, "{\"mechanicId\":\"%s\",\"reason\":\"%s\"}".formatted(ze, "x".repeat(501)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation"))
                .andExpect(jsonPath("$.errors.reason").exists());
        assign(desk, woId, "{\"mechanicId\":\"%s\",\"reason\":\"Rui is off sick today\"}".formatted(ze))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedMechanicId").value(ze));
        // The same mechanic again changes nothing and needs no reason.
        assign(owner, woId, "{\"mechanicId\":\"%s\"}".formatted(ze)).andExpect(status().isOk());
        // Removing the mechanic needs a reason too.
        assign(owner, woId, "{\"mechanicId\":null}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("reason-required"));

        call(get("/api/v1/work-orders/" + woId + "/mechanic"), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mechanicId").value(ze))
                .andExpect(jsonPath("$.mechanicName").value("mechanic"))
                .andExpect(jsonPath("$.changes.length()").value(2))
                .andExpect(jsonPath("$.changes[0].fromMechanicId").isEmpty())
                .andExpect(jsonPath("$.changes[0].toMechanicId").value(rui))
                .andExpect(jsonPath("$.changes[0].reason").isEmpty())
                .andExpect(jsonPath("$.changes[1].fromMechanicId").value(rui))
                .andExpect(jsonPath("$.changes[1].toMechanicId").value(ze))
                .andExpect(jsonPath("$.changes[1].reason").value("Rui is off sick today"))
                .andExpect(jsonPath("$.changes[1].changedByName").value("front_desk"))
                .andExpect(jsonPath("$.changes[1].changedAt").exists());
    }

    @Test
    void should_record_the_mechanic_given_at_check_in_as_the_first_change() throws Exception {
        String owner = signup("Oficina Primeira");
        String rui = invite(owner, "MECHANIC", uniqueEmail("rui"));
        String vehicleId = createVehicle(owner, createCustomer(owner, "Cliente"), "47-AB-55");
        String woId = idOf(call(post("/api/v1/work-orders").content("""
                {"vehicleId":"%s","assignedMechanicId":"%s"}""".formatted(vehicleId, rui)), owner)
                .andExpect(status().isCreated()));

        call(get("/api/v1/work-orders/" + woId + "/mechanic"), owner)
                .andExpect(jsonPath("$.mechanicId").value(rui))
                .andExpect(jsonPath("$.changes.length()").value(1))
                .andExpect(jsonPath("$.changes[0].fromMechanicId").isEmpty())
                .andExpect(jsonPath("$.changes[0].toMechanicId").value(rui))
                .andExpect(jsonPath("$.changes[0].changedByName").value("Owner"));
        // Replacing the mechanic given at check-in needs a reason like any other.
        assign(owner, woId, "{\"mechanicId\":null}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("reason-required"));
    }

    @Test
    void should_keep_the_mechanic_of_a_closed_work_order() throws Exception {
        String owner = signup("Oficina Fechada");
        String rui = invite(owner, "MECHANIC", uniqueEmail("rui"));
        String woId = openWorkOrder(owner, "45-AB-55");
        call(patch("/api/v1/work-orders/" + woId + "/status").content("{\"status\":\"CANCELLED\"}"), owner)
                .andExpect(status().isOk());

        assign(owner, woId, "{\"mechanicId\":\"%s\",\"reason\":\"late\"}".formatted(rui))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("work-order-locked"));
    }

    @Test
    void should_list_active_mechanics_for_the_owner_and_front_desk_only() throws Exception {
        String owner = signup("Oficina Lista");
        String deskEmail = uniqueEmail("desk");
        invite(owner, "FRONT_DESK", deskEmail);
        String mechanicEmail = uniqueEmail("rui");
        String rui = invite(owner, "MECHANIC", mechanicEmail);
        String retired = invite(owner, "MECHANIC", uniqueEmail("old"));
        call(put("/api/v1/users/" + retired).content(withVersion("{\"role\":\"MECHANIC\",\"active\":false}",
                userVersion(owner, retired))), owner).andExpect(status().isOk());

        call(get("/api/v1/mechanics"), login(deskEmail, PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(rui))
                .andExpect(jsonPath("$[0].name").value("mechanic"));
        call(get("/api/v1/mechanics"), login(mechanicEmail, PASSWORD)).andExpect(status().isForbidden());
    }

    @Test
    void should_keep_mechanics_and_other_workshops_out() throws Exception {
        String owner = signup("Oficina Dona Troca");
        String mechanicEmail = uniqueEmail("rui");
        String rui = invite(owner, "MECHANIC", mechanicEmail);
        String woId = openWorkOrder(owner, "46-AB-55");
        assign(owner, woId, "{\"mechanicId\":\"%s\"}".formatted(rui)).andExpect(status().isOk());
        String mechanic = login(mechanicEmail, PASSWORD);

        assign(mechanic, woId, "{\"mechanicId\":null,\"reason\":\"I want out\"}").andExpect(status().isForbidden());
        call(get("/api/v1/work-orders/" + woId + "/mechanic"), mechanic).andExpect(status().isForbidden());

        String other = signup("Oficina Intrusa Troca");
        assign(other, woId, "{\"mechanicId\":null,\"reason\":\"Hacked\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("work-order-not-found"));
        call(get("/api/v1/work-orders/" + woId + "/mechanic"), other).andExpect(status().isNotFound());

        call(get("/api/v1/work-orders/" + woId + "/mechanic"), owner)
                .andExpect(jsonPath("$.mechanicId").value(rui))
                .andExpect(jsonPath("$.changes.length()").value(1));
    }

    private String openWorkOrder(String owner, String plate) throws Exception {
        String vehicleId = createVehicle(owner, createCustomer(owner, "Cliente"), plate);
        return idOf(call(post("/api/v1/work-orders").content("{\"vehicleId\":\"%s\"}".formatted(vehicleId)), owner)
                .andExpect(status().isCreated()));
    }

    private org.springframework.test.web.servlet.ResultActions assign(String token, String woId, String body)
            throws Exception {
        return call(put("/api/v1/work-orders/" + woId + "/mechanic").content(body), token);
    }
}

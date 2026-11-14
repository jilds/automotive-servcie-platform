package com.asp.platform.workshop.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.asp.platform.ApiTestSupport;

class BoardHideAndNotesApiTests extends ApiTestSupport {

    private String deliveredOrder(String owner, String vehicleId) throws Exception {
        String id = idOf(call(post("/api/v1/work-orders").content("{\"vehicleId\":\"%s\"}".formatted(vehicleId)),
                owner).andExpect(status().isCreated()));
        for (String next : new String[] { "IN_PROGRESS", "READY", "DELIVERED" }) {
            call(patch("/api/v1/work-orders/" + id + "/status").content("{\"status\":\"%s\"}".formatted(next)), owner)
                    .andExpect(status().isOk());
        }
        return id;
    }

    @Test
    void should_keep_delivered_orders_on_the_board_until_hidden() throws Exception {
        String owner = signup("Oficina Entregues");
        String customer = createCustomer(owner, "Rui Costa");
        String id = deliveredOrder(owner, createVehicle(owner, customer, "12-AB-34"));
        String other = deliveredOrder(owner, createVehicle(owner, customer, "56-CD-78"));

        call(get("/api/v1/work-orders/board"), owner)
                .andExpect(jsonPath("$.columns.DELIVERED.length()").value(2));

        call(post("/api/v1/work-orders/" + id + "/board-hide"), owner).andExpect(status().isNoContent());
        call(get("/api/v1/work-orders/board"), owner)
                .andExpect(jsonPath("$.columns.DELIVERED.length()").value(1));
        // The status and the history stay.
        call(get("/api/v1/work-orders/" + id), owner).andExpect(jsonPath("$.status").value("DELIVERED"));

        call(post("/api/v1/work-orders/board-hide-delivered"), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hidden").value(1));
        call(get("/api/v1/work-orders/board"), owner)
                .andExpect(jsonPath("$.columns.DELIVERED.length()").value(0));
        call(get("/api/v1/work-orders/" + other), owner).andExpect(jsonPath("$.status").value("DELIVERED"));
    }

    @Test
    void should_only_hide_delivered_orders_and_only_for_owner_and_front_desk() throws Exception {
        String owner = signup("Oficina Esconder");
        String vehicleId = createVehicle(owner, createCustomer(owner, "Rui Costa"), "12-AB-34");
        String open = idOf(call(post("/api/v1/work-orders").content("{\"vehicleId\":\"%s\"}".formatted(vehicleId)),
                owner).andExpect(status().isCreated()));
        call(post("/api/v1/work-orders/" + open + "/board-hide"), owner)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("work-order-not-delivered"));

        String mechanicEmail = uniqueEmail("mec");
        invite(owner, "MECHANIC", mechanicEmail);
        String mechanic = login(mechanicEmail, PASSWORD);
        call(post("/api/v1/work-orders/" + open + "/board-hide"), mechanic).andExpect(status().isForbidden());
        call(post("/api/v1/work-orders/board-hide-delivered"), mechanic).andExpect(status().isForbidden());
    }

    @Test
    void should_not_hide_or_show_another_workshops_delivered_orders() throws Exception {
        String owner = signup("Oficina Um");
        String id = deliveredOrder(owner, createVehicle(owner, createCustomer(owner, "Rui Costa"), "12-AB-34"));
        String stranger = signup("Oficina Dois");

        call(post("/api/v1/work-orders/" + id + "/board-hide"), stranger).andExpect(status().isNotFound());
        call(post("/api/v1/work-orders/board-hide-delivered"), stranger).andExpect(jsonPath("$.hidden").value(0));
        call(get("/api/v1/work-orders/board"), owner).andExpect(jsonPath("$.columns.DELIVERED.length()").value(1));
    }

    @Test
    void should_add_and_list_vehicle_observations_for_every_role() throws Exception {
        String owner = signup("Oficina Notas");
        String vehicleId = createVehicle(owner, createCustomer(owner, "Rui Costa"), "12-AB-34");
        String mechanicEmail = uniqueEmail("mec");
        invite(owner, "MECHANIC", mechanicEmail);
        String mechanic = login(mechanicEmail, PASSWORD);

        call(post("/api/v1/vehicles/" + vehicleId + "/observations").content("{\"body\":\"Pneus gastos\"}"), owner)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("Pneus gastos"));
        call(post("/api/v1/vehicles/" + vehicleId + "/observations").content("{\"body\":\"Ruido na suspensao\"}"),
                mechanic).andExpect(status().isCreated());

        call(get("/api/v1/vehicles/" + vehicleId + "/observations"), mechanic)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].body").value("Ruido na suspensao"));

        call(post("/api/v1/vehicles/" + vehicleId + "/observations").content("{\"body\":\"   \"}"), owner)
                .andExpect(status().isBadRequest());
        call(post("/api/v1/vehicles/" + vehicleId + "/observations").content(
                "{\"body\":\"%s\"}".formatted("x".repeat(2001))), owner).andExpect(status().isBadRequest());
    }

    @Test
    void should_keep_customer_notes_from_mechanics_and_other_workshops() throws Exception {
        String owner = signup("Oficina Clientes");
        String customerId = createCustomer(owner, "Rui Costa");
        call(post("/api/v1/customers/" + customerId + "/notes").content("{\"body\":\"Prefere contacto por SMS\"}"),
                owner).andExpect(status().isCreated());
        call(get("/api/v1/customers/" + customerId + "/notes"), owner)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].body").value("Prefere contacto por SMS"));

        String mechanicEmail = uniqueEmail("mec");
        invite(owner, "MECHANIC", mechanicEmail);
        String mechanic = login(mechanicEmail, PASSWORD);
        call(get("/api/v1/customers/" + customerId + "/notes"), mechanic).andExpect(status().isForbidden());
        call(post("/api/v1/customers/" + customerId + "/notes").content("{\"body\":\"x\"}"), mechanic)
                .andExpect(status().isForbidden());

        String stranger = signup("Outra Oficina");
        call(get("/api/v1/customers/" + customerId + "/notes"), stranger).andExpect(status().isNotFound());
        call(post("/api/v1/customers/" + customerId + "/notes").content("{\"body\":\"x\"}"), stranger)
                .andExpect(status().isNotFound());
    }

    @Test
    void should_not_show_vehicle_observations_of_another_workshop() throws Exception {
        String owner = signup("Oficina Dona");
        String vehicleId = createVehicle(owner, createCustomer(owner, "Rui Costa"), "12-AB-34");
        call(post("/api/v1/vehicles/" + vehicleId + "/observations").content("{\"body\":\"Nota\"}"), owner)
                .andExpect(status().isCreated());
        String stranger = signup("Oficina Alheia");
        call(get("/api/v1/vehicles/" + vehicleId + "/observations"), stranger).andExpect(status().isNotFound());
    }
}

package com.asp.platform.workshop.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.asp.platform.ApiTestSupport;
import com.jayway.jsonpath.JsonPath;

class WorkOrderApiTests extends ApiTestSupport {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void should_run_a_work_order_through_the_board_with_totals_and_role_rules() throws Exception {
        String owner = signup("Oficina Central");
        String vehicleId = createVehicle(owner, createCustomer(owner, "Ana Pereira"), "12-AB-34");

        String mechanicEmail = uniqueEmail("mec");
        String mechanicId = invite(owner, "MECHANIC", mechanicEmail);

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

        String mechanic = login(mechanicEmail, PASSWORD);
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

        // Unassigned: gone from the mechanic's board, but still readable (from the vehicle history), read-only.
        call(put("/api/v1/work-orders/" + woId + "/mechanic").content("{\"reason\":\"Mechanic off sick\"}"), owner)
                .andExpect(status().isOk());
        call(get("/api/v1/work-orders/board"), mechanic)
                .andExpect(jsonPath("$.columns.IN_PROGRESS.length()").value(0));
        call(get("/api/v1/work-orders/" + woId), mechanic)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readOnly").value(true))
                .andExpect(jsonPath("$.allowedNext.length()").value(0))
                .andExpect(jsonPath("$.totalCents").doesNotExist());
        call(patch("/api/v1/work-orders/" + woId + "/status").content("{\"status\":\"READY\"}"), mechanic)
                .andExpect(status().isForbidden());

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
    void should_correct_check_in_data_until_the_car_is_ready() throws Exception {
        String owner = signup("Oficina Check-in");
        String vehicleId = createVehicle(owner, createCustomer(owner, "Sara"), "45-EF-67");
        String woId = idOf(call(post("/api/v1/work-orders").content("""
                {"vehicleId":"%s","mileageIn":100100,"fuelLevel":2,"complaint":"Noise"}""".formatted(vehicleId)),
                owner));

        call(put("/api/v1/work-orders/" + woId + "/check-in").content("""
                {"mileageIn":101000,"fuelLevel":5,"complaint":"Noise when braking","damageNotes":"Dent on bumper"}"""),
                owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mileageIn").value(101000))
                .andExpect(jsonPath("$.fuelLevel").value(5))
                .andExpect(jsonPath("$.complaint").value("Noise when braking"))
                .andExpect(jsonPath("$.damageNotes").value("Dent on bumper"))
                .andExpect(jsonPath("$.status").value("DIAGNOSTICS"));
        call(get("/api/v1/vehicles/" + vehicleId), owner).andExpect(jsonPath("$.mileage").value(101000));

        // A typo fixed downwards changes the order but never lowers the vehicle's odometer.
        call(put("/api/v1/work-orders/" + woId + "/check-in").content("{\"mileageIn\":100200,\"fuelLevel\":5}"),
                owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mileageIn").value(100200))
                .andExpect(jsonPath("$.complaint").doesNotExist());
        call(get("/api/v1/vehicles/" + vehicleId), owner).andExpect(jsonPath("$.mileage").value(101000));
        call(get("/api/v1/work-orders/" + woId), owner).andExpect(jsonPath("$.mileageIn").value(100200));

        call(put("/api/v1/work-orders/" + woId + "/check-in").content("{\"fuelLevel\":9,\"mileageIn\":-1}"), owner)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.fuelLevel").exists())
                .andExpect(jsonPath("$.errors.mileageIn").exists());

        String mechanicEmail = uniqueEmail("mec");
        invite(owner, "MECHANIC", mechanicEmail);
        call(put("/api/v1/work-orders/" + woId + "/check-in").content("{}"), login(mechanicEmail, PASSWORD))
                .andExpect(status().isForbidden());

        call(patch("/api/v1/work-orders/" + woId + "/status").content("{\"status\":\"IN_PROGRESS\"}"), owner);
        call(patch("/api/v1/work-orders/" + woId + "/status").content("{\"status\":\"READY\"}"), owner)
                .andExpect(status().isOk());
        call(put("/api/v1/work-orders/" + woId + "/check-in").content("{\"fuelLevel\":1}"), owner)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/work-order-locked"))
                .andExpect(jsonPath("$.code").value("work-order-locked"));

        call(put("/api/v1/work-orders/00000000-0000-0000-0000-000000000000/check-in").content("{}"), owner)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("work-order-not-found"));
    }

    @Test
    void should_not_let_another_workshop_edit_a_work_order() throws Exception {
        String a = signup("Oficina Ordem A");
        String b = signup("Oficina Ordem B");
        String vehicleId = createVehicle(a, createCustomer(a, "Tiago"), "56-GH-78");
        String woId = idOf(call(post("/api/v1/work-orders").content("""
                {"vehicleId":"%s","complaint":"Oil leak"}""".formatted(vehicleId)), a));

        String lineId = JsonPath.read(call(post("/api/v1/work-orders/" + woId + "/lines").content("""
                {"type":"PART","description":"Filter","quantity":1,"unitPriceCents":1500,"vatRate":23}"""), a)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.lines[0].id");

        call(get("/api/v1/work-orders/" + woId), b)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("work-order-not-found"));
        call(get("/api/v1/vehicles/" + vehicleId + "/work-orders"), b).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        call(put("/api/v1/work-orders/" + woId + "/check-in").content("{\"complaint\":\"Hacked\"}"), b)
                .andExpect(status().isNotFound());
        call(get("/api/v1/work-orders/" + woId), a).andExpect(jsonPath("$.complaint").value("Oil leak"));
        call(post("/api/v1/work-orders/" + woId + "/lines").content("""
                {"type":"LABOR","description":"Hacked","quantity":1,"unitPriceCents":1,"vatRate":23}"""), b)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("work-order-not-found"));
        call(delete("/api/v1/work-orders/" + woId + "/lines/" + lineId), b)
                .andExpect(status().isNotFound());
        call(patch("/api/v1/work-orders/" + woId + "/status").content("{\"status\":\"CANCELLED\"}"), b)
                .andExpect(status().isNotFound());

        call(get("/api/v1/work-orders/" + woId), a)
                .andExpect(jsonPath("$.status").value("DIAGNOSTICS"))
                .andExpect(jsonPath("$.lines.length()").value(1))
                .andExpect(jsonPath("$.lines[0].description").value("Filter"));
    }

    @Test
    void should_reject_a_mechanic_of_another_workshop_with_invalid_mechanic() throws Exception {
        String owner = signup("Oficina Mecânico A");
        String foreignMechanic = invite(signup("Oficina Mecânico B"), "MECHANIC", uniqueEmail("mec"));

        expectInvalidMechanic(owner, foreignMechanic);
    }

    @Test
    void should_reject_an_inactive_mechanic_with_invalid_mechanic() throws Exception {
        String owner = signup("Oficina Mecânico Inativo");
        String retired = invite(owner, "MECHANIC", uniqueEmail("mec"));
        call(put("/api/v1/users/" + retired).content(withVersion("{\"role\":\"MECHANIC\",\"active\":false}",
                userVersion(owner, retired))), owner)
                .andExpect(status().isOk());

        expectInvalidMechanic(owner, retired);
    }

    @Test
    void should_reject_a_user_who_is_not_a_mechanic_with_invalid_mechanic() throws Exception {
        String owner = signup("Oficina Não Mecânico");
        String desk = invite(owner, "FRONT_DESK", uniqueEmail("desk"));

        expectInvalidMechanic(owner, desk);
        expectInvalidMechanic(owner, ownerId(owner));
    }

    /// Check-in and reassignment both refuse the user, on the field that carried the id; a valid mechanic
    /// and unassigning still work.
    private void expectInvalidMechanic(String owner, String userId) throws Exception {
        String vehicleId = createVehicle(owner, createCustomer(owner, "Cliente"), "33-CD-" + nextPlateSuffix());
        call(post("/api/v1/work-orders").content("""
                {"vehicleId":"%s","assignedMechanicId":"%s"}""".formatted(vehicleId, userId)), owner)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/invalid-mechanic"))
                .andExpect(jsonPath("$.code").value("invalid-mechanic"))
                .andExpect(jsonPath("$.errors.assignedMechanicId").exists());

        String woId = idOf(call(post("/api/v1/work-orders").content("""
                {"vehicleId":"%s"}""".formatted(vehicleId)), owner).andExpect(status().isCreated()));
        call(put("/api/v1/work-orders/" + woId + "/mechanic").content("{\"mechanicId\":\"%s\"}".formatted(userId)),
                owner)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid-mechanic"))
                .andExpect(jsonPath("$.errors.mechanicId").exists());
        call(get("/api/v1/work-orders/" + woId), owner).andExpect(jsonPath("$.assignedMechanicId").isEmpty());

        String mechanic = invite(owner, "MECHANIC", uniqueEmail("ok"));
        call(put("/api/v1/work-orders/" + woId + "/mechanic").content("{\"mechanicId\":\"%s\"}".formatted(mechanic)),
                owner).andExpect(status().isOk()).andExpect(jsonPath("$.assignedMechanicId").value(mechanic));
        call(put("/api/v1/work-orders/" + woId + "/mechanic")
                .content("{\"mechanicId\":null,\"reason\":\"Back to the queue\"}"), owner)
                .andExpect(status().isOk());
    }

    @Test
    void should_never_show_a_mechanic_customer_personal_data() throws Exception {
        String owner = signup("Oficina Privacidade");
        String customer = idOf(call(post("/api/v1/customers").content("""
                {"name":"Ana Pereira","nif":"123456789","phone":"+351912345678","email":"ana@cliente.pt"}"""),
                owner).andExpect(status().isCreated()));
        String vehicleId = createVehicle(owner, customer, "44-EF-55");
        String mechanicEmail = uniqueEmail("mec");
        String mechanicId = invite(owner, "MECHANIC", mechanicEmail);
        call(post("/api/v1/work-orders").content("""
                {"vehicleId":"%s","assignedMechanicId":"%s"}""".formatted(vehicleId, mechanicId)), owner)
                .andExpect(status().isCreated());
        String mechanic = login(mechanicEmail, PASSWORD);

        call(get("/api/v1/work-orders/board"), mechanic)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columns.DIAGNOSTICS[0].plate").value("44EF55"))
                .andExpect(jsonPath("$.columns.DIAGNOSTICS[0].customerName").doesNotExist())
                .andExpect(jsonPath("$.columns.DIAGNOSTICS[0].phone").doesNotExist())
                .andExpect(jsonPath("$.columns.DIAGNOSTICS[0].email").doesNotExist())
                .andExpect(jsonPath("$..customerName").doesNotExist())
                .andExpect(jsonPath("$..phone").doesNotExist())
                .andExpect(jsonPath("$..email").doesNotExist());
        call(get("/api/v1/vehicles/" + vehicleId), mechanic)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plate").value("44EF55"))
                .andExpect(jsonPath("$.customerName").doesNotExist())
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    /// The board reads all card vehicles in one query: work orders with their lines (1) plus vehicles (1),
    /// whatever the number of cards.
    @Test
    void should_load_the_board_with_a_constant_number_of_queries() throws Exception {
        String owner = signup("Oficina Consultas");
        String customer = createCustomer(owner, "Frota");
        for (int i = 10; i < 20; i++) {
            String vehicleId = createVehicle(owner, customer, "55-GH-" + i);
            call(post("/api/v1/work-orders").content("{\"vehicleId\":\"%s\"}".formatted(vehicleId)), owner)
                    .andExpect(status().isCreated());
        }
        call(get("/api/v1/work-orders/board"), owner).andExpect(status().isOk());

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        try {
            statistics.clear();
            call(get("/api/v1/work-orders/board"), owner)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.columns.DIAGNOSTICS.length()").value(10))
                    .andExpect(jsonPath("$.columns.DIAGNOSTICS[9].plateDisplay").value("55-GH-19"));
            assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(3);
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }

    private int plateSuffix = 10;

    private String nextPlateSuffix() {
        return String.valueOf(plateSuffix++);
    }

    private String ownerId(String owner) throws Exception {
        String users = call(get("/api/v1/users"), owner).andReturn().getResponse().getContentAsString();
        List<String> owners = JsonPath.read(users, "$[?(@.role == 'OWNER')].id");
        return owners.getFirst();
    }

    @Test
    void should_return_404_when_checking_in_an_unknown_vehicle() throws Exception {
        String owner = signup("Oficina 404");
        call(post("/api/v1/work-orders").content("{\"vehicleId\":\"00000000-0000-0000-0000-000000000000\"}"), owner)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("/problems/vehicle-not-found"))
                .andExpect(jsonPath("$.code").value("vehicle-not-found"));
    }

    @Test
    void should_show_a_mechanic_the_whole_vehicle_history_without_prices_and_read_only() throws Exception {
        String owner = signup("Oficina Historico");
        String vehicleId = createVehicle(owner, createCustomer(owner, "Marta"), "90-XY-12");
        String mineEmail = uniqueEmail("mec");
        String mine = invite(owner, "MECHANIC", mineEmail);
        String other = invite(owner, "MECHANIC", uniqueEmail("mec"));
        String myJob = idOf(call(post("/api/v1/work-orders").content("""
                {"vehicleId":"%s","complaint":"Mine","assignedMechanicId":"%s"}""".formatted(vehicleId, mine)), owner));
        String otherJob = idOf(call(post("/api/v1/work-orders").content("""
                {"vehicleId":"%s","complaint":"Theirs","assignedMechanicId":"%s"}""".formatted(vehicleId, other)),
                owner));
        call(post("/api/v1/work-orders/" + otherJob + "/lines").content("""
                {"type":"PART","description":"Pads","quantity":1,"unitPriceCents":5000,"vatRate":23}"""), owner)
                .andExpect(status().isCreated());
        String mechanic = login(mineEmail, PASSWORD);

        // The history lists every job of the vehicle, never with prices.
        call(get("/api/v1/vehicles/" + vehicleId + "/work-orders"), mechanic)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].totalCents").doesNotExist())
                .andExpect(jsonPath("$[1].totalCents").doesNotExist())
                // Newest first: the other mechanic's job was opened last.
                .andExpect(jsonPath("$[0].id").value(otherJob))
                .andExpect(jsonPath("$[0].readOnly").value(true))
                .andExpect(jsonPath("$[1].id").value(myJob))
                .andExpect(jsonPath("$[1].readOnly").value(false));

        // Another mechanic's job opens read-only: lines without prices, no status moves offered or accepted.
        call(get("/api/v1/work-orders/" + otherJob), mechanic)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complaint").value("Theirs"))
                .andExpect(jsonPath("$.readOnly").value(true))
                .andExpect(jsonPath("$.allowedNext.length()").value(0))
                .andExpect(jsonPath("$.lines[0].description").value("Pads"))
                .andExpect(jsonPath("$.lines[0].unitPriceCents").doesNotExist())
                .andExpect(jsonPath("$.lines[0].netCents").doesNotExist())
                .andExpect(jsonPath("$.totalCents").doesNotExist());
        call(patch("/api/v1/work-orders/" + otherJob + "/status").content("{\"status\":\"IN_PROGRESS\"}"),
                mechanic)
                .andExpect(status().isForbidden());
        call(get("/api/v1/work-orders/" + myJob), mechanic)
                .andExpect(jsonPath("$.readOnly").value(false))
                .andExpect(jsonPath("$.allowedNext").isNotEmpty());

        // The board still shows only the mechanic's own job.
        call(get("/api/v1/work-orders/board"), mechanic)
                .andExpect(jsonPath("$.columns.DIAGNOSTICS.length()").value(1))
                .andExpect(jsonPath("$.columns.DIAGNOSTICS[0].id").value(myJob));
        call(get("/api/v1/work-orders/" + otherJob), owner)
                .andExpect(jsonPath("$.readOnly").value(false))
                .andExpect(jsonPath("$.totalCents").value(6150));
    }
}

package com.asp.platform.booking;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;

/// `POST /work-orders` with a `bookingId`: the booked services still active become catalogue lines, and the booking
/// is CHECKED_IN with the work order, all in one transaction.
class BookingCheckInApiTests extends BookingTestSupport {

    @Test
    void should_open_the_work_order_with_the_booked_services_and_check_the_booking_in() throws Exception {
        Workshop w = workshop("Oficina Entrada Reserva", true);
        String oil = service(w.owner(), "Mudança de óleo", 4000, true, true);
        String brakes = service(w.owner(), "Pastilhas", 6000, true, true);
        String retired = service(w.owner(), "Alinhamento", 3000, true, true);
        openEveryDay(w, 1);
        String vehicle = createVehicle(w.owner(), createCustomer(w.owner(), "Ana Silva"), "AA-00-AA");
        String token = bookAndGetToken(w, freeSlots(w, tomorrow()).getFirst(), oil, brakes, retired);
        String booking = confirmed(w, 1);
        // A service retired since the booking is left out.
        call(put("/api/v1/catalogue/services/" + retired).content(withVersion("""
                {"name":"Alinhamento","standardHours":1,"priceCents":3000,"vatRate":23,"durationMinutes":60,
                 "active":false,"listedOnline":true}""", versionAt(w.owner(), "/api/v1/catalogue/services/" + retired))),
                w.owner())
                .andExpect(status().isOk());

        String order = call(post("/api/v1/work-orders").content("""
                {"vehicleId":"%s","mileageIn":120000,"complaint":"Barulho ao travar","bookingId":"%s"}"""
                .formatted(vehicle, booking)), w.owner())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andExpect(jsonPath("$.lines[0].type").value("LABOR"))
                .andExpect(jsonPath("$.lines[0].serviceItemId").value(oil))
                .andExpect(jsonPath("$.lines[0].unitPriceCents").value(4000))
                .andExpect(jsonPath("$.lines[1].serviceItemId").value(brakes))
                .andReturn().getResponse().getContentAsString();
        String orderId = JsonPath.read(order, "$.id");

        call(get("/api/v1/bookings/" + booking), w.owner())
                .andExpect(jsonPath("$.status").value("CHECKED_IN"))
                .andExpect(jsonPath("$.workOrderId").value(orderId))
                .andExpect(jsonPath("$.vehicleId").value(vehicle));
        publicCall(get(w.publicPath("/bookings/" + token)))
                .andExpect(jsonPath("$.status").value("CHECKED_IN"))
                .andExpect(jsonPath("$.canCancel").value(false));

        // A booking checks in once.
        call(post("/api/v1/work-orders").content("""
                {"vehicleId":"%s","bookingId":"%s"}""".formatted(vehicle, booking)), w.owner())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("booking-not-confirmed"));
        call(get("/api/v1/vehicles/" + vehicle + "/work-orders"), w.owner()).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void should_refuse_a_booking_that_is_not_confirmed_and_open_nothing() throws Exception {
        Workshop w = workshop("Oficina Entrada Pedido", true);
        String oil = service(w.owner(), "Mudança de óleo", 4000, true, true);
        openEveryDay(w, 1);
        String vehicle = createVehicle(w.owner(), createCustomer(w.owner(), "Ana Silva"), "AA-00-AA");
        bookAndGetToken(w, freeSlots(w, tomorrow()).getFirst(), oil);
        String booking = bookingId(w, 1);

        call(post("/api/v1/work-orders").content("""
                {"vehicleId":"%s","mileageIn":150000,"bookingId":"%s"}""".formatted(vehicle, booking)), w.owner())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("booking-not-confirmed"));

        call(get("/api/v1/vehicles/" + vehicle + "/work-orders"), w.owner()).andExpect(jsonPath("$.length()").value(0));
        call(get("/api/v1/vehicles/" + vehicle), w.owner()).andExpect(jsonPath("$.mileage").value(100000));
        call(get("/api/v1/bookings/" + booking), w.owner())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.vehicleId").value(vehicle));
    }

    @Test
    void should_not_check_in_another_workshops_booking() throws Exception {
        Workshop a = workshop("Oficina Entrada A", true);
        Workshop b = workshop("Oficina Entrada B", true);
        String oil = service(a.owner(), "Mudança de óleo", 4000, true, true);
        openEveryDay(a, 1);
        bookAndGetToken(a, freeSlots(a, tomorrow()).getFirst(), oil);
        String booking = confirmed(a, 1);
        String vehicleB = createVehicle(b.owner(), createCustomer(b.owner(), "Rui"), "BB-11-BB");

        call(post("/api/v1/work-orders").content("""
                {"vehicleId":"%s","bookingId":"%s"}""".formatted(vehicleB, booking)), b.owner())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("booking-not-found"));
        call(get("/api/v1/vehicles/" + vehicleB + "/work-orders"), b.owner())
                .andExpect(jsonPath("$.length()").value(0));
        call(get("/api/v1/bookings/" + booking), a.owner()).andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void should_still_check_in_without_a_booking() throws Exception {
        String owner = signup("Oficina Entrada Sem Reserva");
        String vehicle = createVehicle(owner, createCustomer(owner, "Ana"), "CC-22-CC");

        call(post("/api/v1/work-orders").content("{\"vehicleId\":\"%s\"}".formatted(vehicle)), owner)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines.length()").value(0));
    }

    /// Books, confirms, and returns the booking id.
    private String confirmed(Workshop w, int number) throws Exception {
        String id = bookingId(w, number);
        call(post("/api/v1/bookings/" + id + "/confirm").content("{\"version\":0}"), w.owner())
                .andExpect(status().isOk());
        return id;
    }
}

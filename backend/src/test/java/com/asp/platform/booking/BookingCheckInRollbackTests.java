package com.asp.platform.booking;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/// The check-in from a booking is one transaction: when the last step (marking the booking CHECKED_IN) fails after
/// the work order was opened and its catalogue lines added, none of it stays. The failure is injected with a spy,
/// which gives this class its own Spring context.
class BookingCheckInRollbackTests extends BookingTestSupport {

    @MockitoSpyBean
    BookingCheckIns checkIns;

    @Test
    void should_undo_the_work_order_lines_and_mileage_when_the_booking_cannot_be_checked_in() throws Exception {
        Workshop w = workshop("Oficina Entrada Desfeita", true);
        String oil = service(w.owner(), "Mudança de óleo", 4000, true, true);
        openEveryDay(w, 1);
        String vehicle = createVehicle(w.owner(), createCustomer(w.owner(), "Ana Silva"), "AA-00-AA");
        bookAndGetToken(w, freeSlots(w, tomorrow()).getFirst(), oil);
        String booking = bookingId(w, 1);
        call(post("/api/v1/bookings/" + booking + "/confirm").content("{\"version\":0}"), w.owner())
                .andExpect(status().isOk());
        doThrow(new IllegalStateException("injected failure")).when(checkIns).markCheckedIn(any(), any());

        // Not mapped by the exception handlers, so MockMvc rethrows it.
        assertThatThrownBy(() -> call(post("/api/v1/work-orders").content("""
                {"vehicleId":"%s","mileageIn":150000,"bookingId":"%s"}""".formatted(vehicle, booking)), w.owner()))
                .hasRootCauseMessage("injected failure");

        call(get("/api/v1/vehicles/" + vehicle + "/work-orders"), w.owner()).andExpect(jsonPath("$.length()").value(0));
        call(get("/api/v1/vehicles/" + vehicle), w.owner()).andExpect(jsonPath("$.mileage").value(100000));
        call(get("/api/v1/bookings/" + booking), w.owner())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.workOrderId").isEmpty())
                .andExpect(jsonPath("$.vehicleId").value(vehicle));
    }
}

package com.asp.platform.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.asp.platform.shared.TenantScope;
import com.jayway.jsonpath.JsonPath;

/// The front desk's bookings (`/api/v1/bookings`) and the booking settings (`/api/v1/booking/settings`): decisions
/// with versions, roles, settings validation and isolation between workshops.
class BookingApiTests extends BookingTestSupport {

    @Autowired
    JdbcClient jdbc;

    @Test
    void should_list_and_show_bookings_with_the_vehicle_of_the_plate() throws Exception {
        Workshop w = workshop("Oficina Balcao Lista", true);
        String oil = service(w.owner(), "Mudança de óleo", 4000, true, true);
        openEveryDay(w, 2);
        String vehicle = createVehicle(w.owner(), createCustomer(w.owner(), "Ana Silva"), "AA-00-AA");
        List<String> slots = freeSlots(w, tomorrow());
        bookAndGetToken(w, slots.get(1), oil);
        publicCall(post(w.publicPath("/bookings")).content(bookingBody(slots.get(0), "ZZ-99-ZZ", oil)))
                .andExpect(status().isCreated());

        call(get("/api/v1/bookings"), w.owner())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                // Ordered by slot, not by number.
                .andExpect(jsonPath("$[0].number").value(2))
                .andExpect(jsonPath("$[0].vehicleId").isEmpty())
                .andExpect(jsonPath("$[1].number").value(1))
                .andExpect(jsonPath("$[1].vehicleId").value(vehicle))
                .andExpect(jsonPath("$[1].status").value("REQUESTED"))
                .andExpect(jsonPath("$[1].version").value(0))
                .andExpect(jsonPath("$[1].createdAt").exists());
        call(get("/api/v1/bookings?status=CONFIRMED"), w.owner()).andExpect(jsonPath("$.length()").value(0));
        call(get("/api/v1/bookings?from=" + tomorrow().plusDays(1)), w.owner())
                .andExpect(jsonPath("$.length()").value(0));
        call(get("/api/v1/bookings?from=" + tomorrow() + "&to=" + tomorrow()), w.owner())
                .andExpect(jsonPath("$.length()").value(2));
        call(get("/api/v1/bookings/pending-count"), w.owner())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));
        call(get("/api/v1/bookings/" + bookingId(w, 1)), w.owner())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.vehicleId").value(vehicle))
                .andExpect(jsonPath("$.workOrderId").isEmpty())
                .andExpect(jsonPath("$.decidedByName").isEmpty());
    }

    @Test
    void should_confirm_with_the_version_read_and_record_who_decided() throws Exception {
        Workshop w = workshop("Oficina Balcao Confirma", true);
        String oil = service(w.owner(), "Mudança de óleo", 4000, true, true);
        openEveryDay(w, 1);
        String token = bookAndGetToken(w, freeSlots(w, tomorrow()).getFirst(), oil);
        String id = bookingId(w, 1);
        String desk = member(w, "FRONT_DESK");

        call(post("/api/v1/bookings/" + id + "/confirm").content("{\"version\":5}"), w.owner())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("stale-data"));
        call(post("/api/v1/bookings/" + id + "/confirm").content("{}"), w.owner())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.version").exists());
        call(post("/api/v1/bookings/" + id + "/confirm").content("{\"version\":0}"), desk)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.decidedByName").value("front_desk"))
                .andExpect(jsonPath("$.decidedAt").exists())
                .andExpect(jsonPath("$.version").value(1));
        call(post("/api/v1/bookings/" + id + "/confirm").content("{\"version\":1}"), w.owner())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("booking-status"));
        publicCall(get(w.publicPath("/bookings/" + token))).andExpect(jsonPath("$.status").value("CONFIRMED"));
        call(get("/api/v1/bookings/pending-count"), w.owner()).andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void should_decline_with_a_reason_the_customer_sees() throws Exception {
        Workshop w = workshop("Oficina Balcao Recusa", true);
        String oil = service(w.owner(), "Mudança de óleo", 4000, true, true);
        openEveryDay(w, 1);
        String slot = freeSlots(w, tomorrow()).getFirst();
        String token = bookAndGetToken(w, slot, oil);
        String id = bookingId(w, 1);

        call(post("/api/v1/bookings/" + id + "/decline").content("{\"reason\":\" \",\"version\":0}"), w.owner())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("decline-reason-required"))
                .andExpect(jsonPath("$.errors.reason").exists());
        call(post("/api/v1/bookings/" + id + "/decline").content("{\"reason\":\"Sem peças\",\"version\":0}"),
                w.owner())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"))
                .andExpect(jsonPath("$.declineReason").value("Sem peças"));

        publicCall(get(w.publicPath("/bookings/" + token)))
                .andExpect(jsonPath("$.status").value("DECLINED"))
                .andExpect(jsonPath("$.declineReason").value("Sem peças"))
                .andExpect(jsonPath("$.canCancel").value(false));
        // The place is free again.
        assertThat(freeSlots(w, tomorrow())).contains(slot);
    }

    @Test
    void should_move_to_a_free_slot_and_confirm() throws Exception {
        Workshop w = workshop("Oficina Balcao Muda", true);
        String oil = service(w.owner(), "Mudança de óleo", 4000, true, true);
        openEveryDay(w, 1);
        List<String> slots = freeSlots(w, tomorrow());
        String token = bookAndGetToken(w, slots.get(0), oil);
        bookAndGetToken(w, slots.get(2), oil);
        String id = bookingId(w, 1);

        // Full, off the grid, in the past: 409 slot-unavailable.
        for (String bad : new String[] {slots.get(2), Instant.parse(slots.get(1)).plusSeconds(900).toString(),
                Instant.now().minusSeconds(7200).toString()}) {
            call(post("/api/v1/bookings/" + id + "/move").content(
                    "{\"slotStart\":\"%s\",\"version\":0}".formatted(bad)), w.owner())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("slot-unavailable"));
        }
        call(post("/api/v1/bookings/" + id + "/move").content(
                "{\"slotStart\":\"%s\",\"version\":0}".formatted(slots.get(1))), w.owner())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.slotStart").value(slots.get(1)))
                .andExpect(jsonPath("$.version").value(1));
        // Its own slot is full (one car, its own place): only the slots the staff list shows are accepted.
        call(post("/api/v1/bookings/" + id + "/move").content(
                "{\"slotStart\":\"%s\",\"version\":1}".formatted(slots.get(1))), w.owner())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("slot-unavailable"));

        publicCall(get(w.publicPath("/bookings/" + token))).andExpect(jsonPath("$.slotStart").value(slots.get(1)));
        assertThat(freeSlots(w, tomorrow())).contains(slots.get(0)).doesNotContain(slots.get(1), slots.get(2));
    }

    @Test
    void should_list_staff_slots_without_lead_time_or_horizon_and_move_exactly_to_them() throws Exception {
        Workshop w = workshop("Oficina Balcao Vagas", true);
        String oil = service(w.owner(), "Mudança de óleo", 4000, true, true);
        openEveryDay(w, 1);
        List<String> tomorrowSlots = freeSlots(w, tomorrow());
        bookAndGetToken(w, tomorrowSlots.get(0), oil);
        bookAndGetToken(w, tomorrowSlots.get(1), oil);
        String id = bookingId(w, 1);
        java.time.LocalDate later = tomorrow().plusDays(5);
        // Now the public page offers nothing tomorrow (lead time 30 days) and nothing after tomorrow (horizon 1 day);
        // a closure the day after.
        call(put("/api/v1/booking/settings").content(withVersion(("""
                {"slotMinutes":60,"carsPerSlot":1,"leadTimeHours":720,"horizonDays":1,"cancelUntilHours":0,
                 "openingHours":[%s],"closures":[{"day":"%s","reason":"Feriado"}]}""").formatted(everyDay(),
                tomorrow().plusDays(1)), versionAt(w.owner(), "/api/v1/booking/settings"))), w.owner())
                .andExpect(status().isOk());
        assertThat(freeSlots(w, tomorrow())).isEmpty();
        assertThat(freeSlots(w, later)).isEmpty();

        String body = call(get("/api/v1/bookings/slots?from=" + tomorrow() + "&days=6"), w.owner())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].date").value(tomorrow().toString()))
                // Twelve slots minus the two booked ones; the closed day has none; after the horizon all twelve.
                .andExpect(jsonPath("$[0].slots.length()").value(10))
                .andExpect(jsonPath("$[0].slots[0].time").value("10:00"))
                .andExpect(jsonPath("$[0].slots[0].free").value(1))
                .andExpect(jsonPath("$[1].slots.length()").value(0))
                .andExpect(jsonPath("$[5].slots.length()").value(12))
                .andReturn().getResponse().getContentAsString();
        List<String> listedLater = JsonPath.read(body, "$[5].slots[*].start");
        // Today: only slots that have not started.
        List<String> today = JsonPath.read(call(get("/api/v1/bookings/slots?days=1"), w.owner())
                .andReturn().getResponse().getContentAsString(), "$[0].slots[*].start");
        assertThat(today).allSatisfy(start -> assertThat(Instant.parse(start)).isAfter(Instant.now()));

        // A listed slot after the horizon is accepted; a booked (full) or closed-day slot is not.
        call(post("/api/v1/bookings/" + id + "/move").content(
                "{\"slotStart\":\"%s\",\"version\":0}".formatted(listedLater.getFirst())), w.owner())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slotStart").value(listedLater.getFirst()));
        String closedDay = tomorrow().plusDays(1).atTime(9, 0).atZone(LISBON).toInstant().toString();
        for (String bad : new String[] {tomorrowSlots.get(1), closedDay}) {
            call(post("/api/v1/bookings/" + id + "/move").content(
                    "{\"slotStart\":\"%s\",\"version\":1}".formatted(bad)), w.owner())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("slot-unavailable"));
        }
        // The freed slot is listed again, the taken one no longer.
        call(get("/api/v1/bookings/slots?from=" + tomorrow() + "&days=1"), w.owner())
                .andExpect(jsonPath("$[0].slots.length()").value(11))
                .andExpect(jsonPath("$[0].slots[0].start").value(tomorrowSlots.get(0)));
        call(get("/api/v1/bookings/slots?from=" + later + "&days=1"), w.owner())
                .andExpect(jsonPath("$[0].slots.length()").value(11));

        String mechanic = member(w, "MECHANIC");
        call(get("/api/v1/bookings/slots"), mechanic).andExpect(status().isForbidden());
        call(get("/api/v1/bookings/slots?days=99"), member(w, "FRONT_DESK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(14));
    }

    private static String everyDay() {
        StringBuilder hours = new StringBuilder();
        for (int day = 1; day <= 7; day++) {
            hours.append(day == 1 ? "" : ",")
                    .append("{\"weekday\":%d,\"opens\":\"08:00\",\"closes\":\"20:00\"}".formatted(day));
        }
        return hours.toString();
    }

    @Test
    void should_mark_a_no_show_only_after_the_slot_started() throws Exception {
        Workshop w = workshop("Oficina Balcao Falta", true);
        String oil = service(w.owner(), "Mudança de óleo", 4000, true, true);
        openEveryDay(w, 1);
        bookAndGetToken(w, freeSlots(w, tomorrow()).getFirst(), oil);
        String id = bookingId(w, 1);
        call(post("/api/v1/bookings/" + id + "/confirm").content("{\"version\":0}"), w.owner())
                .andExpect(status().isOk());

        call(post("/api/v1/bookings/" + id + "/no-show").content("{\"version\":1}"), w.owner())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("booking-status"));

        // The slot passes.
        TenantScope.runAs(UUID.fromString(w.id()), () -> jdbc.sql(
                "update booking set slot_start = now() - interval '2 hours' where id = ?::uuid")
                .param(id).update());
        call(post("/api/v1/bookings/" + id + "/no-show").content("{\"version\":1}"), w.owner())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_SHOW"));
    }

    @Test
    void should_keep_bookings_from_mechanics_and_settings_writes_for_the_owner() throws Exception {
        Workshop w = workshop("Oficina Balcao Papeis", true);
        String oil = service(w.owner(), "Mudança de óleo", 4000, true, true);
        openEveryDay(w, 1);
        bookAndGetToken(w, freeSlots(w, tomorrow()).getFirst(), oil);
        String id = bookingId(w, 1);
        String mechanic = member(w, "MECHANIC");
        String desk = member(w, "FRONT_DESK");

        call(get("/api/v1/bookings"), mechanic).andExpect(status().isForbidden());
        call(get("/api/v1/bookings/" + id), mechanic).andExpect(status().isForbidden());
        call(get("/api/v1/bookings/pending-count"), mechanic).andExpect(status().isForbidden());
        call(post("/api/v1/bookings/" + id + "/confirm").content("{\"version\":0}"), mechanic)
                .andExpect(status().isForbidden());
        call(post("/api/v1/bookings/" + id + "/decline").content("{\"reason\":\"x\",\"version\":0}"), mechanic)
                .andExpect(status().isForbidden());
        call(get("/api/v1/booking/settings"), mechanic).andExpect(status().isForbidden());

        call(get("/api/v1/booking/settings"), desk).andExpect(status().isOk());
        call(put("/api/v1/booking/settings").content(withVersion(settingsBody("[]", "[]", 30, 1), 0)), desk)
                .andExpect(status().isForbidden());
        call(get("/api/v1/bookings/" + id), desk).andExpect(status().isOk());
    }

    @Test
    void should_read_defaults_before_the_first_save_and_save_with_versions() throws Exception {
        Workshop w = workshop("Oficina Balcao Definicoes", true);

        call(get("/api/v1/booking/settings"), w.owner())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slotMinutes").value(30))
                .andExpect(jsonPath("$.carsPerSlot").value(1))
                .andExpect(jsonPath("$.leadTimeHours").value(24))
                .andExpect(jsonPath("$.horizonDays").value(30))
                .andExpect(jsonPath("$.cancelUntilHours").value(24))
                .andExpect(jsonPath("$.openingHours.length()").value(10))
                .andExpect(jsonPath("$.openingHours[0].weekday").value(1))
                .andExpect(jsonPath("$.openingHours[0].opens").value("09:00"))
                .andExpect(jsonPath("$.openingHours[0].closes").value("13:00"))
                .andExpect(jsonPath("$.openingHours[1].opens").value("14:00"))
                .andExpect(jsonPath("$.closures.length()").value(0))
                .andExpect(jsonPath("$.version").value(0));

        String hours = "[{\"weekday\":6,\"opens\":\"08:30\",\"closes\":\"12:30\"},"
                + "{\"weekday\":1,\"opens\":\"14:00\",\"closes\":\"18:30\"},"
                + "{\"weekday\":1,\"opens\":\"08:30\",\"closes\":\"12:30\"}]";
        String closures = "[{\"day\":\"2026-12-25\",\"reason\":\"Natal\"},{\"day\":\"2026-12-25\",\"reason\":\"x\"}]";
        call(put("/api/v1/booking/settings").content(withVersion(settingsBody(hours, closures, 15, 3), 0)),
                w.owner())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slotMinutes").value(15))
                .andExpect(jsonPath("$.carsPerSlot").value(3))
                .andExpect(jsonPath("$.openingHours.length()").value(3))
                .andExpect(jsonPath("$.openingHours[0].weekday").value(1))
                .andExpect(jsonPath("$.openingHours[0].opens").value("08:30"))
                .andExpect(jsonPath("$.openingHours[2].weekday").value(6))
                .andExpect(jsonPath("$.closures.length()").value(1))
                .andExpect(jsonPath("$.closures[0].reason").value("Natal"));
        long version = versionAt(w.owner(), "/api/v1/booking/settings");
        assertThat(version).isPositive();

        call(put("/api/v1/booking/settings").content(withVersion(settingsBody(hours, "[]", 30, 1), 0)), w.owner())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("stale-data"));
        call(put("/api/v1/booking/settings").content(withVersion(settingsBody(hours, "[]", 30, 1), version)),
                w.owner())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closures.length()").value(0))
                .andExpect(jsonPath("$.version").value(version + 1));
    }

    @Test
    void should_validate_the_settings() throws Exception {
        Workshop w = workshop("Oficina Balcao Validacao", true);

        String overlapping = "[{\"weekday\":2,\"opens\":\"09:00\",\"closes\":\"13:00\"},"
                + "{\"weekday\":2,\"opens\":\"12:00\",\"closes\":\"18:00\"}]";
        String empty = "[{\"weekday\":2,\"opens\":\"13:00\",\"closes\":\"13:00\"}]";
        for (String hours : new String[] {overlapping, empty}) {
            call(put("/api/v1/booking/settings").content(withVersion(settingsBody(hours, "[]", 30, 1), 0)),
                    w.owner())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("invalid-opening-hours"))
                    .andExpect(jsonPath("$.errors.openingHours").exists());
        }
        call(put("/api/v1/booking/settings").content(withVersion(settingsBody("[]", "[]", 20, 0), 0)), w.owner())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation"))
                .andExpect(jsonPath("$.errors.slotMinutes").exists())
                .andExpect(jsonPath("$.errors.carsPerSlot").exists());
        call(put("/api/v1/booking/settings").content(withVersion(settingsBody(
                "[{\"weekday\":8,\"opens\":\"09:00\",\"closes\":\"10:00\"}]", "[]", 30, 1), 0)), w.owner())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['openingHours[0].weekday']").exists());
        call(get("/api/v1/booking/settings"), w.owner()).andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void should_close_the_public_slots_on_closures() throws Exception {
        Workshop w = workshop("Oficina Balcao Fecho", true);
        saveSettings(w, 1, 0, "[{\"day\":\"%s\",\"reason\":\"Feriado\"}]".formatted(tomorrow()));

        assertThat(freeSlots(w, tomorrow())).isEmpty();
        assertThat(freeSlots(w, tomorrow().plusDays(1))).hasSize(12);
    }

    @Test
    void should_keep_bookings_and_settings_inside_their_workshop() throws Exception {
        Workshop a = workshop("Oficina Balcao Isolada A", true);
        Workshop b = workshop("Oficina Balcao Isolada B", true);
        String oil = service(a.owner(), "Mudança de óleo", 4000, true, true);
        openEveryDay(a, 2);
        bookAndGetToken(a, freeSlots(a, tomorrow()).getFirst(), oil);
        String id = bookingId(a, 1);

        call(get("/api/v1/bookings"), b.owner()).andExpect(jsonPath("$.length()").value(0));
        call(get("/api/v1/bookings/pending-count"), b.owner()).andExpect(jsonPath("$.count").value(0));
        call(get("/api/v1/bookings/" + id), b.owner())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("booking-not-found"));
        for (String action : new String[] {"confirm", "no-show"}) {
            call(post("/api/v1/bookings/" + id + "/" + action).content("{\"version\":0}"), b.owner())
                    .andExpect(status().isNotFound());
        }
        call(post("/api/v1/bookings/" + id + "/decline").content("{\"reason\":\"x\",\"version\":0}"), b.owner())
                .andExpect(status().isNotFound());
        // B's settings are its own: still the defaults, and B's slots ignore A's booking.
        call(get("/api/v1/booking/settings"), b.owner())
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.slotMinutes").value(30));
        openEveryDay(b, 2);
        call(get("/api/v1/booking/settings"), a.owner()).andExpect(jsonPath("$.carsPerSlot").value(2));
        publicCall(get(b.publicPath("/slots?from=" + tomorrow() + "&days=1")))
                .andExpect(jsonPath("$[0].slots[0].free").value(2));
        call(get("/api/v1/bookings/" + id), a.owner()).andExpect(jsonPath("$.status").value("REQUESTED"));
        String count = call(get("/api/v1/bookings"), a.owner()).andReturn().getResponse().getContentAsString();
        assertThat((Integer) JsonPath.read(count, "$.length()")).isEqualTo(1);
    }

    @Test
    void should_list_at_most_124_days_at_once() throws Exception {
        Workshop w = workshop("Oficina Balcao Periodo", true);
        java.time.LocalDate today = java.time.LocalDate.now(LISBON);

        // The "to confirm" list asks a month back and three months ahead.
        call(get("/api/v1/bookings?from=" + today.minusDays(30) + "&to=" + today.plusDays(90)), w.owner())
                .andExpect(status().isOk());
        call(get("/api/v1/bookings?from=" + today + "&to=" + today.plusDays(124)), w.owner())
                .andExpect(status().isOk());
        call(get("/api/v1/bookings?from=" + today + "&to=" + today.plusDays(125)), w.owner())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation"))
                .andExpect(jsonPath("$.errors.to").exists());
    }

    @Test
    void should_give_the_settings_in_the_workshops_time_zone() throws Exception {
        Workshop w = workshop("Oficina Balcao Fuso", true);

        call(get("/api/v1/booking/settings"), w.owner())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeZone").value("Europe/Lisbon"));
        call(put("/api/v1/booking/settings").content(withVersion(settingsBody("[]", "[]", 30, 1), 0)), w.owner())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeZone").value("Europe/Lisbon"));
        call(put("/api/v1/workshop/settings").content(withVersion("""
                {"name":"Oficina Balcao Fuso","country":"BR","timezone":"America/Sao_Paulo","defaultVatRate":17,
                 "labourRateCents":4500}""", versionAt(w.owner(), "/api/v1/workshop/settings"))), w.owner())
                .andExpect(status().isOk());
        call(get("/api/v1/booking/settings"), w.owner()).andExpect(jsonPath("$.timeZone").value("America/Sao_Paulo"));
    }

    @Test
    void should_keep_the_referrer_on_this_origin_on_staff_and_public_answers() throws Exception {
        Workshop w = workshop("Oficina Balcao Referrer", true);

        call(get("/api/v1/bookings"), w.owner())
                .andExpect(header().string("Referrer-Policy", "same-origin"));
        publicCall(get(w.publicPath("/bookings/" + com.asp.platform.shared.StatusTokens.generate())))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Referrer-Policy", "same-origin"));
        mvc.perform(get("/w/" + w.slug() + "/booking/abc")).andExpect(header().string("Referrer-Policy", "same-origin"));
    }

    @Test
    void should_give_the_last_place_to_either_a_staff_move_or_a_public_booking_racing_for_it() throws Exception {
        Workshop w = workshop("Oficina Balcao Corrida", true);
        String oil = service(w.owner(), "Mudança de óleo", 4000, true, true);
        openEveryDay(w, 1);
        List<String> slots = freeSlots(w, tomorrow());
        bookAndGetToken(w, slots.get(0), oil);
        String id = bookingId(w, 1);
        String target = slots.get(1);

        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Future<String> move = pool.submit(() -> {
                start.await();
                return answer(call(post("/api/v1/bookings/" + id + "/move").content(
                        "{\"slotStart\":\"%s\",\"version\":0}".formatted(target)), w.owner()));
            });
            java.util.concurrent.Future<String> book = pool.submit(() -> {
                start.await();
                return answer(book(w, target, oil));
            });
            start.countDown();
            List<String> answers = new java.util.ArrayList<>(List.of(move.get(30, java.util.concurrent.TimeUnit.SECONDS),
                    book.get(30, java.util.concurrent.TimeUnit.SECONDS)));
            // One of them wins (200 move or 201 booking), the other is refused.
            assertThat(answers).filteredOn(a -> a.startsWith("20")).hasSize(1);
            assertThat(answers).filteredOn(a -> a.equals("409 slot-unavailable")).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
        List<String> starts = JsonPath.read(call(get("/api/v1/bookings"), w.owner())
                .andReturn().getResponse().getContentAsString(), "$[?(@.status != 'CANCELLED')].slotStart");
        assertThat(starts).filteredOn(target::equals).hasSize(1);
    }

    /// "201", or "409 slot-unavailable".
    private static String answer(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        var response = result.andReturn().getResponse();
        return response.getStatus() < 300 ? String.valueOf(response.getStatus())
                : response.getStatus() + " " + JsonPath.read(response.getContentAsString(), "$.code");
    }

    private static String settingsBody(String hours, String closures, int slotMinutes, int cars) {
        return """
                {"slotMinutes":%d,"carsPerSlot":%d,"leadTimeHours":12,"horizonDays":60,"cancelUntilHours":6,
                 "openingHours":%s,"closures":%s}""".formatted(slotMinutes, cars, hours, closures);
    }
}

package com.asp.platform.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.asp.platform.shared.StatusTokens;
import com.jayway.jsonpath.JsonPath;

/// The booking pages of the public site (`/api/v1/public/{slug}/booking`): services, slots, booking, status link
/// and cancel, with the honeypot, the privacy consent, the add-on switch and the capacity race.
class PublicBookingApiTests extends BookingTestSupport {

    @Test
    void should_list_only_active_services_listed_online_with_their_price_with_vat() throws Exception {
        Workshop w = workshop("Oficina Reserva Servicos", true);
        String oil = service(w.owner(), "Mudança de óleo", 4000, true, true);
        service(w.owner(), "Só no balcão", 1000, false, true);
        service(w.owner(), "Retirado", 1000, true, false);
        call(put("/api/v1/workshop/settings").content(withVersion("""
                {"name":"Oficina Reserva Servicos","country":"PT","timezone":"Europe/Lisbon","defaultVatRate":23,
                 "labourRateCents":4500}""", versionAt(w.owner(), "/api/v1/workshop/settings"))), w.owner())
                .andExpect(status().isOk());
        String hourly = idOf(call(post("/api/v1/catalogue/services").content("""
                {"name":"Diagnóstico","standardHours":0.5,"pricing":"HOURLY","priceCents":0,"vatRate":23,
                 "durationMinutes":30,"listedOnline":true}"""), w.owner()));

        publicCall(get(w.publicPath("/services")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(hourly))
                // 0.5 h × 45.00 = 22.50 before VAT; with 23 % 27.675, rounded half up.
                .andExpect(jsonPath("$[0].priceCents").value(2250))
                .andExpect(jsonPath("$[0].priceWithVatCents").value(2768))
                .andExpect(jsonPath("$[0].durationMinutes").value(30))
                .andExpect(jsonPath("$[1].id").value(oil))
                .andExpect(jsonPath("$[1].name").value("Mudança de óleo"))
                .andExpect(jsonPath("$[1].description").value("Descrição"))
                .andExpect(jsonPath("$[1].category").value("Manutenção"))
                .andExpect(jsonPath("$[1].vatRate").value(23))
                .andExpect(jsonPath("$[1].priceWithVatCents").value(4920));
    }

    @Test
    void should_book_a_free_slot_show_its_status_and_let_the_customer_cancel() throws Exception {
        Workshop w = workshop("Oficina Reserva Fluxo", true);
        String oil = service(w.owner(), "Mudança de óleo", 4000, true, true);
        openEveryDay(w, 2);
        LocalDate day = tomorrow();

        publicCall(get(w.publicPath("/slots?from=" + day + "&days=2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].date").value(day.toString()))
                .andExpect(jsonPath("$[0].slots.length()").value(12))
                .andExpect(jsonPath("$[0].slots[0].time").value("08:00"))
                .andExpect(jsonPath("$[0].slots[0].free").value(2));
        String slot = freeSlots(w, day).getFirst();
        assertThat(Instant.parse(slot)).isEqualTo(day.atTime(8, 0).atZone(LISBON).toInstant());

        String body = book(w, slot, oil, oil)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.slotStart").value(slot))
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.statusToken");
        assertThat(token).hasSize(StatusTokens.LENGTH);

        publicCall(get(w.publicPath("/slots?from=" + day + "&days=1")))
                .andExpect(jsonPath("$[0].slots[0].start").value(slot))
                .andExpect(jsonPath("$[0].slots[0].free").value(1));
        publicCall(get(w.publicPath("/bookings/" + token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.slotStart").value(slot))
                .andExpect(jsonPath("$.workshopName").value("Oficina Reserva Fluxo"))
                .andExpect(jsonPath("$.timeZone").value("Europe/Lisbon"))
                .andExpect(jsonPath("$.services.length()").value(1))
                .andExpect(jsonPath("$.services[0].name").value("Mudança de óleo"))
                .andExpect(jsonPath("$.services[0].durationMinutes").value(60))
                .andExpect(jsonPath("$.services[0].priceWithVatCents").value(4920))
                .andExpect(jsonPath("$.canCancel").value(true))
                .andExpect(jsonPath("$.declineReason").isEmpty())
                // No contact data or ids on the public status.
                .andExpect(jsonPath("$.name").doesNotExist())
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.id").doesNotExist());

        publicCall(post(w.publicPath("/bookings/" + token + "/cancel")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.timeZone").value("Europe/Lisbon"))
                .andExpect(jsonPath("$.canCancel").value(false));
        publicCall(post(w.publicPath("/bookings/" + token + "/cancel")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("booking-status"));
        // The cancelled booking gave its place back.
        publicCall(get(w.publicPath("/slots?from=" + day + "&days=1")))
                .andExpect(jsonPath("$[0].slots[0].free").value(2));
    }

    @Test
    void should_store_the_contact_snapshot_with_the_plate_normalised() throws Exception {
        Workshop w = workshop("Oficina Reserva Matricula", true);
        String oil = service(w.owner(), "Mudança de óleo", 4000, true, true);
        openEveryDay(w, 1);
        String slot = freeSlots(w, tomorrow()).getFirst();

        publicCall(post(w.publicPath("/bookings")).content(bookingBody(slot, " aa-00 aa ", oil)))
                .andExpect(status().isCreated());

        call(get("/api/v1/bookings/" + bookingId(w, 1)), w.owner())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plate").value("AA00AA"))
                .andExpect(jsonPath("$.name").value("Ana Silva"))
                .andExpect(jsonPath("$.phone").value("912345678"))
                .andExpect(jsonPath("$.email").value("ana@example.pt"))
                .andExpect(jsonPath("$.note").value("Barulho ao travar"))
                .andExpect(jsonPath("$.marketingConsent").value(false))
                .andExpect(jsonPath("$.services[0].serviceId").value(oil))
                .andExpect(jsonPath("$.services[0].priceCents").value(4000));
    }

    @Test
    void should_refuse_slots_that_are_not_offered_or_full() throws Exception {
        Workshop w = workshop("Oficina Reserva Lotada", true);
        String oil = service(w.owner(), "Mudança de óleo", 4000, true, true);
        openEveryDay(w, 1);
        String slot = freeSlots(w, tomorrow()).getFirst();

        book(w, slot, oil).andExpect(status().isCreated());
        book(w, slot, oil).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("slot-unavailable"));
        // Off the grid, in the past, after the horizon.
        for (String bad : new String[] {Instant.parse(slot).plusSeconds(600).toString(),
                Instant.now().minusSeconds(3600).toString(), Instant.parse(slot).plusSeconds(40L * 86400).toString()}) {
            book(w, bad, oil).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("slot-unavailable"));
        }
        assertThat(freeSlots(w, tomorrow())).doesNotContain(slot).hasSize(11);
    }

    @Test
    void should_give_one_place_to_one_of_two_requests_racing_for_it() throws Exception {
        Workshop w = workshop("Oficina Reserva Corrida", true);
        String oil = service(w.owner(), "Mudança de óleo", 4000, true, true);
        openEveryDay(w, 1);
        String slot = freeSlots(w, tomorrow()).getFirst();

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return book(w, slot, oil).andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get(30, TimeUnit.SECONDS));
            }
            Collections.sort(statuses);
            assertThat(statuses).containsExactly(201, 409);
        } finally {
            pool.shutdownNow();
        }
        call(get("/api/v1/bookings"), w.owner()).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void should_answer_a_filled_hidden_field_like_a_success_and_store_nothing() throws Exception {
        Workshop w = workshop("Oficina Reserva Robo", true);
        String oil = service(w.owner(), "Mudança de óleo", 4000, true, true);
        openEveryDay(w, 1);
        String slot = freeSlots(w, tomorrow()).getFirst();
        String body = bookingBody(slot, "AA-00-AA", oil).replace("\"website\":\"\"", "\"website\":\"http://spam\"");

        String answer = publicCall(post(w.publicPath("/bookings")).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").isNumber())
                .andExpect(jsonPath("$.slotStart").value(slot))
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(answer, "$.statusToken");
        assertThat(token).hasSize(StatusTokens.LENGTH);

        call(get("/api/v1/bookings"), w.owner()).andExpect(jsonPath("$.length()").value(0));
        publicCall(get(w.publicPath("/bookings/" + token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("booking-not-found"));
        assertThat(freeSlots(w, tomorrow())).contains(slot);
    }

    @Test
    void should_need_the_privacy_consent() throws Exception {
        Workshop w = workshop("Oficina Reserva Privacidade", true);
        String oil = service(w.owner(), "Mudança de óleo", 4000, true, true);
        openEveryDay(w, 1);
        String slot = freeSlots(w, tomorrow()).getFirst();

        for (String consent : new String[] {"false", "null"}) {
            publicCall(post(w.publicPath("/bookings")).content(bookingBody(slot, "AA-00-AA", oil)
                    .replace("\"privacyAccepted\":true", "\"privacyAccepted\":" + consent)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("privacy-consent-required"))
                    .andExpect(jsonPath("$.errors.privacyAccepted").exists());
        }
        call(get("/api/v1/bookings"), w.owner()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void should_validate_the_booking_form() throws Exception {
        Workshop w = workshop("Oficina Reserva Validacao", true);
        String oil = service(w.owner(), "Mudança de óleo", 4000, true, true);
        openEveryDay(w, 1);
        String slot = freeSlots(w, tomorrow()).getFirst();
        String valid = bookingBody(slot, "AA-00-AA", oil);

        expectInvalid(w, valid.replace("\"serviceIds\":[\"" + oil + "\"]", "\"serviceIds\":[]"), "serviceIds");
        String eleven = String.join(",", Collections.nCopies(11, "\"" + oil + "\""));
        expectInvalid(w, valid.replace("\"serviceIds\":[\"" + oil + "\"]", "\"serviceIds\":[" + eleven + "]"),
                "serviceIds");
        expectInvalid(w, valid.replace("\"phone\":\"912345678\"", "\"phone\":\"\"")
                .replace("\"email\":\"ana@example.pt\"", "\"email\":null"), "phone")
                .andExpect(jsonPath("$.errors.email").exists());
        expectInvalid(w, valid.replace("\"name\":\"Ana Silva\"", "\"name\":\"" + "x".repeat(121) + "\""), "name");
        expectInvalid(w, valid.replace("Barulho ao travar", "x".repeat(501)), "note");
        expectInvalid(w, valid.replace("\"plate\":\"AA-00-AA\"", "\"plate\":\" \""), "plate");
        expectInvalid(w, valid.replace("\"plate\":\"AA-00-AA\"", "\"plate\":\"--\""), "plate");
        expectInvalid(w, valid.replace("\"plate\":\"AA-00-AA\"", "\"plate\":\" - \""), "plate");
        expectInvalid(w, valid.replace("ana@example.pt", "not-an-email"), "email");

        // A phone alone, or an e-mail alone, is enough.
        publicCall(post(w.publicPath("/bookings")).content(valid.replace("\"phone\":\"912345678\"", "\"phone\":null")))
                .andExpect(status().isCreated());
    }

    @Test
    void should_refuse_services_that_are_not_offered_online() throws Exception {
        Workshop w = workshop("Oficina Reserva Fora", true);
        String counterOnly = service(w.owner(), "Só no balcão", 1000, false, true);
        String retired = service(w.owner(), "Retirado", 1000, true, false);
        Workshop other = workshop("Oficina Reserva Outra", true);
        String othersService = service(other.owner(), "Da outra", 1000, true, true);
        openEveryDay(w, 1);
        String slot = freeSlots(w, tomorrow()).getFirst();

        for (String id : new String[] {counterOnly, retired, othersService, "6f1c1d1e-0000-4000-8000-000000000000"}) {
            book(w, slot, id).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("service-not-found"));
        }
    }

    @Test
    void should_refuse_to_cancel_after_the_deadline() throws Exception {
        Workshop w = workshop("Oficina Reserva Prazo", true);
        String oil = service(w.owner(), "Mudança de óleo", 4000, true, true);
        saveSettings(w, 1, 720, "[]");
        String token = bookAndGetToken(w, freeSlots(w, tomorrow()).getFirst(), oil);

        publicCall(get(w.publicPath("/bookings/" + token))).andExpect(jsonPath("$.canCancel").value(false));
        publicCall(post(w.publicPath("/bookings/" + token + "/cancel")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("booking-cancel-too-late"));
    }

    @Test
    void should_answer_add_on_not_available_on_every_booking_page_while_the_add_on_is_off() throws Exception {
        Workshop w = workshop("Oficina Reserva Desligada", false);
        String oil = service(w.owner(), "Mudança de óleo", 4000, true, true);
        String slot = tomorrow().atTime(9, 0).atZone(LISBON).toInstant().toString();

        for (ResultActions result : List.of(publicCall(get(w.publicPath("/services"))),
                publicCall(get(w.publicPath("/slots"))),
                book(w, slot, oil),
                publicCall(get(w.publicPath("/bookings/" + StatusTokens.generate()))),
                publicCall(post(w.publicPath("/bookings/" + StatusTokens.generate() + "/cancel"))))) {
            result.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("add-on-not-available"));
        }
    }

    @Test
    void should_keep_status_links_inside_their_workshop() throws Exception {
        Workshop a = workshop("Oficina Reserva Link A", true);
        Workshop b = workshop("Oficina Reserva Link B", true);
        String oil = service(a.owner(), "Mudança de óleo", 4000, true, true);
        openEveryDay(a, 1);
        String token = bookAndGetToken(a, freeSlots(a, tomorrow()).getFirst(), oil);

        publicCall(get(b.publicPath("/bookings/" + token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("booking-not-found"));
        publicCall(post(b.publicPath("/bookings/" + token + "/cancel")))
                .andExpect(status().isNotFound());
        publicCall(get(a.publicPath("/bookings/" + token))).andExpect(jsonPath("$.status").value("REQUESTED"));
        publicCall(get(a.publicPath("/bookings/not-a-token"))).andExpect(status().isNotFound());
    }

    @Test
    void should_offer_the_default_hours_before_the_owner_saves_settings() throws Exception {
        Workshop w = workshop("Oficina Reserva Padrao", true);
        // Default lead time 24 h: the day after tomorrow is fully inside it.
        LocalDate day = LocalDate.now(LISBON).plusDays(2);
        while (day.getDayOfWeek().getValue() > 5) {
            day = day.plusDays(1);
        }

        publicCall(get(w.publicPath("/slots?from=" + day + "&days=1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slots.length()").value(16))
                .andExpect(jsonPath("$[0].slots[0].time").value("09:00"))
                .andExpect(jsonPath("$[0].slots[15].time").value("17:30"));
        // At most 14 days a request.
        publicCall(get(w.publicPath("/slots?days=40"))).andExpect(jsonPath("$.length()").value(14));
    }

    @Test
    void should_keep_counting_booking_requests_against_the_public_write_limit() throws Exception {
        Workshop w = workshop("Oficina Reserva Limite", true);
        String oil = service(w.owner(), "Mudança de óleo", 4000, true, true);
        openEveryDay(w, 5);
        String slot = freeSlots(w, tomorrow()).getFirst();
        String address = randomAddress();

        // Test limit: 3 writes an hour per address and workshop.
        for (int i = 0; i < 3; i++) {
            mvc.perform(fromClient(address, post(w.publicPath("/bookings")).content(bookingBody(slot, "AA-00-AA", oil))))
                    .andExpect(status().isCreated());
        }
        mvc.perform(fromClient(address, post(w.publicPath("/bookings")).content(bookingBody(slot, "AA-00-AA", oil))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("too-many-requests"));
        // Reads have their own budget.
        mvc.perform(fromClient(address, get(w.publicPath("/services")))).andExpect(status().isOk());
    }

    private ResultActions expectInvalid(Workshop w, String body, String field) throws Exception {
        return publicCall(post(w.publicPath("/bookings")).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation"))
                .andExpect(jsonPath("$.errors." + field).exists());
    }
}

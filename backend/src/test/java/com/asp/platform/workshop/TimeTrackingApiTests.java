package com.asp.platform.workshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;

/// Time tracking over the API: one running timer per mechanic, who may start and stop, auto-stop, corrections with a
/// reason, totals per order and per line, and what each role may read (docs/api/release-4.md "Workshop floor").
class TimeTrackingApiTests extends FloorTestSupport {

    @Test
    void should_start_and_stop_the_timer_of_the_assigned_mechanic() throws Exception {
        Shop g = garage("Tempo Basico");
        Mechanic rui = mechanic(g);
        String wo = orderOf(g, rui);

        String started = call(post("/api/v1/work-orders/" + wo + "/time/start"), rui.token())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.workOrderId").value(wo))
                .andExpect(jsonPath("$.mechanicId").value(rui.id()))
                .andExpect(jsonPath("$.mechanicName").value("mechanic"))
                .andExpect(jsonPath("$.running").value(true))
                .andExpect(jsonPath("$.stoppedAt").isEmpty())
                .andExpect(jsonPath("$.lineId").isEmpty())
                .andExpect(jsonPath("$.autoStopped").value(false))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn().getResponse().getContentAsString();

        call(get("/api/v1/work-orders/" + wo + "/time"), rui.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running.id").value(idIn(started)))
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.now").exists());

        call(post("/api/v1/work-orders/" + wo + "/time/stop"), rui.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(idIn(started)))
                .andExpect(jsonPath("$.running").value(false))
                .andExpect(jsonPath("$.stoppedAt").exists())
                .andExpect(jsonPath("$.autoStopped").value(false));

        call(post("/api/v1/work-orders/" + wo + "/time/stop"), rui.token())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("timer-not-running"));
        call(get("/api/v1/work-orders/" + wo + "/time"), rui.token())
                .andExpect(jsonPath("$.running").isEmpty());
    }

    @Test
    void should_keep_one_running_timer_per_mechanic() throws Exception {
        Shop g = garage("Tempo Unico");
        Mechanic rui = mechanic(g);
        String a = orderOf(g, rui);
        String b = orderOf(g, rui);

        String first = startTimer(rui.token(), a);
        // The same order and line again changes nothing.
        call(post("/api/v1/work-orders/" + a + "/time/start"), rui.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(idIn(first)));

        // Starting another job stops the first one.
        String second = startTimer(rui.token(), b);
        call(get("/api/v1/work-orders/" + a + "/time"), rui.token())
                .andExpect(jsonPath("$.running").isEmpty())
                .andExpect(jsonPath("$.entries[0].running").value(false))
                .andExpect(jsonPath("$.entries[0].autoStopped").value(false));
        call(get("/api/v1/time/running"), rui.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entry.id").value(idIn(second)))
                .andExpect(jsonPath("$.workOrderNumber").isNumber())
                .andExpect(jsonPath("$.plateDisplay").value("AA-01-AA"));

        // Another line of the same order also replaces the running entry.
        String line = addLine(g, b, "Travões", 3000, 23);
        call(post("/api/v1/work-orders/" + b + "/time/start").content("{\"lineId\":\"" + line + "\"}"),
                rui.token())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lineId").value(line));
        assertThat(JsonPath.<Integer>read(timeSummary(rui.token(), b), "$.entries.length()")).isEqualTo(2);
        assertThat(JsonPath.<String>read(timeSummary(rui.token(), b), "$.running.lineId")).isEqualTo(line);
    }

    @Test
    void should_report_no_running_timer_for_the_signed_in_user() throws Exception {
        Shop g = garage("Tempo Livre");
        call(get("/api/v1/time/running"), g.owner())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entry").isEmpty())
                .andExpect(jsonPath("$.workOrderNumber").isEmpty());
    }

    @Test
    void should_let_only_the_assigned_mechanic_or_the_owner_run_the_timer() throws Exception {
        Shop g = garage("Tempo Permissoes");
        Mechanic rui = mechanic(g);
        Mechanic ze = mechanic(g);
        String desk = member(g, "FRONT_DESK");
        String wo = orderOf(g, rui);

        call(post("/api/v1/work-orders/" + wo + "/time/start"), ze.token()).andExpect(status().isForbidden());
        call(post("/api/v1/work-orders/" + wo + "/time/start"), desk).andExpect(status().isForbidden());
        call(post("/api/v1/work-orders/" + wo + "/time/start"), rui.token()).andExpect(status().isCreated());
        call(post("/api/v1/work-orders/" + wo + "/time/stop"), ze.token()).andExpect(status().isForbidden());
        call(post("/api/v1/work-orders/" + wo + "/time/stop"), desk).andExpect(status().isForbidden());

        // The owner stops it for the mechanic, and starts it again for them.
        call(post("/api/v1/work-orders/" + wo + "/time/stop"), g.owner()).andExpect(status().isOk());
        call(post("/api/v1/work-orders/" + wo + "/time/start"), g.owner())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mechanicId").value(rui.id()));

        // Everyone reads, mechanics who are not assigned included.
        call(get("/api/v1/work-orders/" + wo + "/time"), ze.token()).andExpect(status().isOk());
        call(get("/api/v1/work-orders/" + wo + "/time"), desk).andExpect(status().isOk());
    }

    @Test
    void should_refuse_the_owner_a_timer_on_an_order_without_mechanic_and_a_line_of_another_order()
            throws Exception {
        Shop g = garage("Tempo Sem Mecanico");
        String unassigned = workOrder(g, vehicle(g, true), null);
        call(post("/api/v1/work-orders/" + unassigned + "/time/start"), g.owner())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("no-mechanic-assigned"));

        Mechanic rui = mechanic(g);
        String wo = orderOf(g, rui);
        String otherLine = addLine(g, unassigned, "Outra", 1000, 23);
        call(post("/api/v1/work-orders/" + wo + "/time/start").content("{\"lineId\":\"" + otherLine + "\"}"),
                rui.token())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation"))
                .andExpect(jsonPath("$.errors.lineId").exists());
    }

    @Test
    void should_stop_the_timer_when_the_order_is_ready_or_cancelled_or_changes_mechanic() throws Exception {
        Shop g = garage("Tempo Auto Stop");
        Mechanic rui = mechanic(g);
        Mechanic ze = mechanic(g);

        String ready = orderOf(g, rui);
        startTimer(rui.token(), ready);
        moveOk(rui.token(), ready, "IN_PROGRESS");
        moveOk(rui.token(), ready, "READY");
        call(get("/api/v1/work-orders/" + ready + "/time"), g.owner())
                .andExpect(jsonPath("$.running").isEmpty())
                .andExpect(jsonPath("$.entries[0].autoStopped").value(true))
                .andExpect(jsonPath("$.entries[0].stoppedAt").exists());
        call(post("/api/v1/work-orders/" + ready + "/time/start"), rui.token())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("time-tracking-closed"));
        // Back to work: the timer can start again.
        moveOk(g.owner(), ready, "IN_PROGRESS");
        startTimer(rui.token(), ready);

        String cancelled = orderOf(g, rui);
        startTimer(rui.token(), cancelled);
        moveOk(g.owner(), cancelled, "CANCELLED");
        call(get("/api/v1/work-orders/" + cancelled + "/time"), g.owner())
                .andExpect(jsonPath("$.running").isEmpty())
                .andExpect(jsonPath("$.entries[0].autoStopped").value(true));
        call(post("/api/v1/work-orders/" + cancelled + "/time/start"), g.owner())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("time-tracking-closed"));

        String handed = orderOf(g, rui);
        startTimer(rui.token(), handed);
        call(put("/api/v1/work-orders/" + handed + "/mechanic")
                .content("{\"mechanicId\":\"%s\",\"reason\":\"Rui is off\"}".formatted(ze.id())), g.owner())
                .andExpect(status().isOk());
        call(get("/api/v1/work-orders/" + handed + "/time"), g.owner())
                .andExpect(jsonPath("$.running").isEmpty())
                .andExpect(jsonPath("$.entries[0].autoStopped").value(true));
        // The old mechanic is free to start elsewhere, and the new one starts here.
        startTimer(ze.token(), handed);
    }

    @Test
    void should_total_the_time_per_order_and_per_line_with_corrections_and_missed_entries() throws Exception {
        Shop g = garage("Tempo Totais");
        Mechanic rui = mechanic(g);
        String desk = member(g, "FRONT_DESK");
        String wo = orderOf(g, rui);
        String line = addLine(g, wo, "Travões", 3000, 23);
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        String missed = call(post("/api/v1/work-orders/" + wo + "/time-entries").content("""
                {"mechanicId":"%s","lineId":"%s","startedAt":"%s","stoppedAt":"%s","reason":"Esqueceu-se de iniciar"}"""
                .formatted(rui.id(), line, now.minus(5, ChronoUnit.HOURS), now.minus(3, ChronoUnit.HOURS))), desk)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seconds").value(7200))
                .andExpect(jsonPath("$.editReason").value("Esqueceu-se de iniciar"))
                .andExpect(jsonPath("$.editedByName").value("front_desk"))
                .andExpect(jsonPath("$.running").value(false))
                .andReturn().getResponse().getContentAsString();
        call(post("/api/v1/work-orders/" + wo + "/time-entries").content("""
                {"mechanicId":"%s","startedAt":"%s","stoppedAt":"%s","reason":"Diagnostico"}"""
                .formatted(rui.id(), now.minus(2, ChronoUnit.HOURS), now.minus(90, ChronoUnit.MINUTES))), g.owner())
                .andExpect(status().isCreated());

        call(get("/api/v1/work-orders/" + wo + "/time"), rui.token())
                .andExpect(jsonPath("$.totalSeconds").value(9000))
                .andExpect(jsonPath("$.unassignedSeconds").value(1800))
                .andExpect(jsonPath("$.byLine.length()").value(1))
                .andExpect(jsonPath("$.byLine[0].lineId").value(line))
                .andExpect(jsonPath("$.byLine[0].seconds").value(7200));

        // Correct the first entry: one hour, on the whole order. Time goes from the line to "unassigned".
        call(put("/api/v1/work-orders/" + wo + "/time-entries/" + idIn(missed)).content("""
                {"startedAt":"%s","stoppedAt":"%s","lineId":null,"reason":"Eram so 2 horas","version":0}"""
                .formatted(now.minus(5, ChronoUnit.HOURS), now.minus(4, ChronoUnit.HOURS))), desk)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seconds").value(3600))
                .andExpect(jsonPath("$.lineId").isEmpty())
                .andExpect(jsonPath("$.editReason").value("Eram so 2 horas"))
                .andExpect(jsonPath("$.version").value(1));
        call(get("/api/v1/work-orders/" + wo + "/time"), rui.token())
                .andExpect(jsonPath("$.totalSeconds").value(5400))
                .andExpect(jsonPath("$.unassignedSeconds").value(5400))
                .andExpect(jsonPath("$.byLine.length()").value(0));
    }

    @Test
    void should_validate_corrections() throws Exception {
        Shop g = garage("Tempo Correcoes");
        Mechanic rui = mechanic(g);
        String desk = member(g, "FRONT_DESK");
        String wo = orderOf(g, rui);
        String entry = idIn(startTimer(rui.token(), wo));
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        String path = "/api/v1/work-orders/" + wo + "/time-entries/" + entry;
        String body = """
                {"startedAt":"%s","stoppedAt":%s,"reason":%s,"version":%d}""";

        // A reason is required.
        call(put(path).content(body.formatted(now.minus(1, ChronoUnit.HOURS), "null", "\"  \"", 0)), desk)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("reason-required"))
                .andExpect(jsonPath("$.errors.reason").exists());
        // No time in the future, no stop before the start.
        call(put(path).content(body.formatted(now.plus(1, ChronoUnit.HOURS), "null", "\"x\"", 0)), desk)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid-time-range"))
                .andExpect(jsonPath("$.errors.startedAt").exists());
        call(put(path).content(body.formatted(now.minus(1, ChronoUnit.HOURS),
                "\"" + now.minus(2, ChronoUnit.HOURS) + "\"", "\"x\"", 0)), desk)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid-time-range"))
                .andExpect(jsonPath("$.errors.stoppedAt").exists());
        call(put(path).content(body.formatted(now.minus(1, ChronoUnit.HOURS),
                "\"" + now.plus(1, ChronoUnit.HOURS) + "\"", "\"x\"", 0)), desk)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid-time-range"));
        // A running entry stays running when no stop is given; the start moves.
        call(put(path).content(body.formatted(now.minus(1, ChronoUnit.HOURS), "null", "\"Comecou mais cedo\"", 0)),
                desk)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(true))
                .andExpect(jsonPath("$.version").value(1));
        // A stale version.
        call(put(path).content(body.formatted(now.minus(1, ChronoUnit.HOURS), "null", "\"x\"", 0)), desk)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("stale-data"));
        // A stopped entry needs a stop.
        call(post("/api/v1/work-orders/" + wo + "/time/stop"), rui.token()).andExpect(status().isOk());
        long version = JsonPath.<Number>read(timeSummary(desk, wo), "$.entries[0].version").longValue();
        call(put(path).content(body.formatted(now.minus(1, ChronoUnit.HOURS), "null", "\"x\"", version)), desk)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid-time-range"))
                .andExpect(jsonPath("$.errors.stoppedAt").exists());

        // Mechanics do not correct; an entry of another order is not found; the mechanic of a missed entry must exist.
        call(put(path).content(body.formatted(now.minus(1, ChronoUnit.HOURS), "null", "\"x\"", version)),
                rui.token()).andExpect(status().isForbidden());
        String other = orderOf(g, rui);
        call(put("/api/v1/work-orders/" + other + "/time-entries/" + entry)
                .content(body.formatted(now.minus(1, ChronoUnit.HOURS), "\"" + now + "\"", "\"x\"", version)), desk)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("time-entry-not-found"));
        call(post("/api/v1/work-orders/" + wo + "/time-entries").content("""
                {"mechanicId":"%s","startedAt":"%s","stoppedAt":"%s","reason":"x"}"""
                .formatted(java.util.UUID.randomUUID(), now.minus(2, ChronoUnit.HOURS),
                        now.minus(1, ChronoUnit.HOURS))), desk)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid-mechanic"));
        call(post("/api/v1/work-orders/" + wo + "/time-entries").content("""
                {"mechanicId":"%s","startedAt":"%s","stoppedAt":"%s","reason":"x"}"""
                .formatted(rui.id(), now.minus(2, ChronoUnit.HOURS), now.minus(1, ChronoUnit.HOURS))), rui.token())
                .andExpect(status().isForbidden());
        // Corrections stay possible after delivery.
        moveOk(g.owner(), wo, "IN_PROGRESS");
        moveOk(g.owner(), wo, "READY");
        moveOk(g.owner(), wo, "DELIVERED");
        call(post("/api/v1/work-orders/" + wo + "/time-entries").content("""
                {"mechanicId":"%s","startedAt":"%s","stoppedAt":"%s","reason":"Depois da entrega"}"""
                .formatted(rui.id(), now.minus(3, ChronoUnit.HOURS), now.minus(2, ChronoUnit.HOURS))), desk)
                .andExpect(status().isCreated());
    }

    @Test
    void should_not_show_time_of_another_workshop() throws Exception {
        Shop a = garage("Tempo A");
        Shop b = garage("Tempo B");
        Mechanic rui = mechanic(a);
        String wo = orderOf(a, rui);
        String entry = idIn(startTimer(rui.token(), wo));

        call(get("/api/v1/work-orders/" + wo + "/time"), b.owner())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("work-order-not-found"));
        call(post("/api/v1/work-orders/" + wo + "/time/stop"), b.owner())
                .andExpect(status().isNotFound());
        call(put("/api/v1/work-orders/" + wo + "/time-entries/" + entry).content("""
                {"startedAt":"%s","stoppedAt":null,"reason":"x","version":0}""".formatted(Instant.now().minusSeconds(60))),
                b.owner()).andExpect(status().isNotFound());
    }

    @Test
    void should_refuse_overlapping_time_of_one_mechanic_and_keep_the_history_of_corrections() throws Exception {
        Shop g = garage("Tempo Sobreposto");
        Mechanic rui = mechanic(g);
        String desk = member(g, "FRONT_DESK");
        String a = orderOf(g, rui);
        String b = orderOf(g, rui);
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        String missed = """
                {"mechanicId":"%s","startedAt":"%s","stoppedAt":"%s","reason":"Esquecido"}""";

        String first = idIn(call(post("/api/v1/work-orders/" + a + "/time-entries").content(missed.formatted(
                rui.id(), now.minus(5, ChronoUnit.HOURS), now.minus(3, ChronoUnit.HOURS))), desk)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        // Another order, same mechanic, overlapping: refused. Adjacent is fine.
        call(post("/api/v1/work-orders/" + b + "/time-entries").content(missed.formatted(rui.id(),
                now.minus(4, ChronoUnit.HOURS), now.minus(2, ChronoUnit.HOURS))), desk)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("time-entry-overlap"));
        String second = idIn(call(post("/api/v1/work-orders/" + b + "/time-entries").content(missed.formatted(
                rui.id(), now.minus(3, ChronoUnit.HOURS), now.minus(2, ChronoUnit.HOURS))), desk)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        // A correction that would overlap is refused and changes nothing; no history row is written for it.
        String body = """
                {"startedAt":"%s","stoppedAt":"%s","lineId":null,"reason":"Corrigir","version":%d}""";
        call(put("/api/v1/work-orders/" + b + "/time-entries/" + second).content(body.formatted(
                now.minus(4, ChronoUnit.HOURS), now.minus(2, ChronoUnit.HOURS), 0)), desk)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("time-entry-overlap"));
        call(get("/api/v1/work-orders/" + b + "/time-entries/" + second + "/changes"), desk)
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        // A timer cannot start inside booked time either: today's start is after every entry, so it works.
        startTimer(rui.token(), a);

        // A valid correction keeps what the entry was.
        call(put("/api/v1/work-orders/" + a + "/time-entries/" + first).content(body.formatted(
                now.minus(6, ChronoUnit.HOURS), now.minus(3, ChronoUnit.HOURS), 0)), desk)
                .andExpect(status().isOk());
        call(get("/api/v1/work-orders/" + a + "/time-entries/" + first + "/changes"), desk)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].oldStartedAt").value(now.minus(5, ChronoUnit.HOURS).toString()))
                .andExpect(jsonPath("$[0].oldStoppedAt").value(now.minus(3, ChronoUnit.HOURS).toString()))
                .andExpect(jsonPath("$[0].oldLineId").isEmpty())
                .andExpect(jsonPath("$[0].changedByName").value("front_desk"))
                .andExpect(jsonPath("$[0].reason").value("Corrigir"));
        call(get("/api/v1/work-orders/" + a + "/time-entries/" + first + "/changes"), rui.token())
                .andExpect(status().isForbidden());
        call(get("/api/v1/work-orders/" + b + "/time-entries/" + first + "/changes"), desk)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("time-entry-not-found"));
    }
}

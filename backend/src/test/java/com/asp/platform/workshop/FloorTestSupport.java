package com.asp.platform.workshop;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

/// Workshop floor tests: mechanics with tokens, work orders assigned to them, status moves and the workshop setting.
public abstract class FloorTestSupport extends QuoteTestSupport {

    /// A mechanic of the workshop: user id and bearer token.
    protected record Mechanic(String id, String token) {
    }

    protected Mechanic mechanic(Shop g) throws Exception {
        String email = uniqueEmail("mecanico");
        String id = invite(g.owner(), "MECHANIC", email);
        return new Mechanic(id, login(email, PASSWORD));
    }

    /// A work order in DIAGNOSTICS assigned to the mechanic.
    protected String orderOf(Shop g, Mechanic m) throws Exception {
        return workOrder(g, vehicle(g, true), m.id());
    }

    protected ResultActions move(String token, String wo, String status) throws Exception {
        return call(patch("/api/v1/work-orders/" + wo + "/status").content("{\"status\":\"" + status + "\"}"), token);
    }

    protected void moveOk(String token, String wo, String status) throws Exception {
        move(token, wo, status).andExpect(status().isOk());
    }

    /// Switches the workshop's delivery checklist gate.
    protected void requireDeliveryChecklist(Shop g, boolean required) throws Exception {
        call(put("/api/v1/workshop/settings").content(withVersion("""
                {"name":"Oficina","country":"PT","timezone":"Europe/Lisbon","defaultVatRate":23,
                 "labourRateCents":0,"deliveryChecklistRequired":%s}""".formatted(required),
                versionAt(g.owner(), "/api/v1/workshop/settings"))), g.owner()).andExpect(status().isOk());
    }

    protected String startTimer(String token, String wo) throws Exception {
        return call(post("/api/v1/work-orders/" + wo + "/time/start"), token).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    protected String timeSummary(String token, String wo) throws Exception {
        return call(get("/api/v1/work-orders/" + wo + "/time"), token).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString();
    }

    /// Adds a labour line to the order and returns the id of the new line.
    protected String addLine(Shop g, String wo, String description, long cents, int vat) throws Exception {
        String body = labour(g, wo, description, cents, vat).andReturn().getResponse().getContentAsString();
        java.util.List<String> ids = JsonPath.read(body, "$.lines[?(@.description=='" + description + "')].id");
        return ids.getFirst();
    }

    protected static String idIn(String body) {
        return JsonPath.read(body, "$.id");
    }
}

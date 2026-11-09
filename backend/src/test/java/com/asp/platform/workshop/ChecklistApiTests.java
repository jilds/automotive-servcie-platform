package com.asp.platform.workshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;

/// Checklist templates (defaults, saving, versions), the copy a work order takes when it starts one, the answers, who
/// may do what, and the DELIVERED gate (docs/api/release-4.md "Workshop floor").
class ChecklistApiTests extends FloorTestSupport {

    private static final String TWO_ITEMS = """
            {"items":[{"label":"Chaves recebidas","required":true},{"label":"Lavagem","required":false}],
             "version":%d}""";

    @Test
    void should_offer_the_default_templates_until_the_owner_saves_one() throws Exception {
        Shop g = garage("Lista Padrao");
        call(get("/api/v1/checklist-templates"), g.owner())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].kind").value("CHECK_IN"))
                .andExpect(jsonPath("$[0].custom").value(false))
                .andExpect(jsonPath("$[0].version").value(0))
                .andExpect(jsonPath("$[0].items.length()").value(6))
                .andExpect(jsonPath("$[0].items[1].label").value("Chaves recebidas"))
                .andExpect(jsonPath("$[0].items[1].required").value(true))
                .andExpect(jsonPath("$[0].items[5].required").value(false))
                .andExpect(jsonPath("$[1].kind").value("DELIVERY"))
                .andExpect(jsonPath("$[1].items.length()").value(6));
        call(get("/api/v1/checklist-templates"), member(g, "FRONT_DESK")).andExpect(status().isOk());
        call(get("/api/v1/checklist-templates"), mechanic(g).token()).andExpect(status().isForbidden());
    }

    @Test
    void should_save_a_template_with_a_version_and_only_for_the_owner() throws Exception {
        Shop g = garage("Lista Guardar");
        call(put("/api/v1/checklist-templates/CHECK_IN").content(TWO_ITEMS.formatted(0)), member(g, "FRONT_DESK"))
                .andExpect(status().isForbidden());
        call(put("/api/v1/checklist-templates/CHECK_IN").content(TWO_ITEMS.formatted(0)), mechanic(g).token())
                .andExpect(status().isForbidden());

        call(put("/api/v1/checklist-templates/CHECK_IN").content(TWO_ITEMS.formatted(0)), g.owner())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("CHECK_IN"))
                .andExpect(jsonPath("$.custom").value(true))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].label").value("Chaves recebidas"))
                .andExpect(jsonPath("$.items[1].required").value(false));
        // The same version again is stale; the next one replaces the items.
        call(put("/api/v1/checklist-templates/CHECK_IN").content(TWO_ITEMS.formatted(0)), g.owner())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("stale-data"));
        call(put("/api/v1/checklist-templates/CHECK_IN").content("""
                {"items":[{"label":"  So um  ","required":true}],"version":1}"""), g.owner())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].label").value("So um"));

        call(get("/api/v1/checklist-templates"), g.owner())
                .andExpect(jsonPath("$[0].custom").value(true))
                .andExpect(jsonPath("$[0].items.length()").value(1))
                .andExpect(jsonPath("$[1].custom").value(false));
    }

    @Test
    void should_validate_the_template() throws Exception {
        Shop g = garage("Lista Validar");
        call(put("/api/v1/checklist-templates/DELIVERY").content("{\"items\":[],\"version\":0}"), g.owner())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation"))
                .andExpect(jsonPath("$.errors.items").exists());
        call(put("/api/v1/checklist-templates/DELIVERY").content("""
                {"items":[{"label":"%s","required":true}],"version":0}""".formatted("x".repeat(121))), g.owner())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation"));
        call(put("/api/v1/checklist-templates/DELIVERY").content("""
                {"items":[{"label":"   ","required":true}],"version":0}"""), g.owner())
                .andExpect(status().isBadRequest());
        String thirtyOne = String.join(",", java.util.Collections.nCopies(31, "{\"label\":\"x\",\"required\":true}"));
        call(put("/api/v1/checklist-templates/DELIVERY").content("{\"items\":[" + thirtyOne + "],\"version\":0}"),
                g.owner()).andExpect(status().isBadRequest());
        call(put("/api/v1/checklist-templates/OTHER").content(TWO_ITEMS.formatted(0)), g.owner())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation"));
        call(get("/api/v1/checklist-templates"), g.owner()).andExpect(jsonPath("$[1].custom").value(false));
    }

    @Test
    void should_copy_the_template_when_a_checklist_starts_and_keep_the_copy() throws Exception {
        Shop g = garage("Lista Copia");
        Mechanic rui = mechanic(g);
        String wo = orderOf(g, rui);
        call(put("/api/v1/checklist-templates/CHECK_IN").content(TWO_ITEMS.formatted(0)), g.owner())
                .andExpect(status().isOk());

        call(get("/api/v1/work-orders/" + wo + "/checklists"), rui.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryChecklistRequired").value(false))
                .andExpect(jsonPath("$.checklists.length()").value(0));

        String started = call(post("/api/v1/work-orders/" + wo + "/checklists/CHECK_IN"), rui.token())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("CHECK_IN"))
                .andExpect(jsonPath("$.startedByName").value("mechanic"))
                .andExpect(jsonPath("$.requiredTotal").value(1))
                .andExpect(jsonPath("$.requiredAnswered").value(0))
                .andExpect(jsonPath("$.complete").value(false))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].position").value(1))
                .andExpect(jsonPath("$.items[0].label").value("Chaves recebidas"))
                .andExpect(jsonPath("$.items[0].required").value(true))
                .andExpect(jsonPath("$.items[0].result").isEmpty())
                .andReturn().getResponse().getContentAsString();
        String itemId = JsonPath.read(started, "$.items[0].id");

        // Starting again returns the same checklist; a later template change does not touch it.
        call(put("/api/v1/checklist-templates/CHECK_IN").content("""
                {"items":[{"label":"Outro","required":true}],"version":1}"""), g.owner()).andExpect(status().isOk());
        call(post("/api/v1/work-orders/" + wo + "/checklists/CHECK_IN"), g.owner())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(itemId))
                .andExpect(jsonPath("$.items[0].label").value("Chaves recebidas"));

        // The answer, with a note; then another answer replaces it.
        call(put("/api/v1/work-orders/" + wo + "/checklists/CHECK_IN/items/" + itemId)
                .content("{\"result\":\"ATTENTION\",\"note\":\"  So uma chave  \"}"), rui.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].result").value("ATTENTION"))
                .andExpect(jsonPath("$.items[0].note").value("So uma chave"))
                .andExpect(jsonPath("$.items[0].answeredByName").value("mechanic"))
                .andExpect(jsonPath("$.items[0].answeredAt").exists())
                .andExpect(jsonPath("$.requiredAnswered").value(1))
                .andExpect(jsonPath("$.complete").value(true));
        call(put("/api/v1/work-orders/" + wo + "/checklists/CHECK_IN/items/" + itemId)
                .content("{\"result\":\"N_A\",\"note\":null}"), member(g, "FRONT_DESK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].result").value("N_A"))
                .andExpect(jsonPath("$.items[0].note").isEmpty())
                .andExpect(jsonPath("$.items[0].answeredByName").value("front_desk"));

        call(get("/api/v1/work-orders/" + wo + "/checklists"), g.owner())
                .andExpect(jsonPath("$.checklists.length()").value(1))
                .andExpect(jsonPath("$.checklists[0].items[0].result").value("N_A"))
                .andExpect(jsonPath("$.checklists[0].items[1].result").isEmpty());
    }

    @Test
    void should_validate_answers_and_permissions() throws Exception {
        Shop g = garage("Lista Respostas");
        Mechanic rui = mechanic(g);
        Mechanic ze = mechanic(g);
        String wo = orderOf(g, rui);
        String other = orderOf(g, rui);

        // Not started: nothing to answer.
        call(put("/api/v1/work-orders/" + wo + "/checklists/DELIVERY/items/" + java.util.UUID.randomUUID())
                .content("{\"result\":\"OK\"}"), g.owner())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("checklist-not-found"));

        call(post("/api/v1/work-orders/" + wo + "/checklists/DELIVERY"), ze.token()).andExpect(status().isForbidden());
        call(post("/api/v1/work-orders/" + wo + "/checklists/BOGUS"), g.owner()).andExpect(status().isBadRequest());
        String started = call(post("/api/v1/work-orders/" + wo + "/checklists/DELIVERY"), rui.token())
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String itemId = JsonPath.read(started, "$.items[0].id");
        String path = "/api/v1/work-orders/" + wo + "/checklists/DELIVERY/items/";

        call(put(path + itemId).content("{\"result\":\"OK\"}"), ze.token()).andExpect(status().isForbidden());
        call(put(path + itemId).content("{\"result\":\"MAYBE\"}"), rui.token()).andExpect(status().isBadRequest());
        call(put(path + itemId).content("{}"), rui.token()).andExpect(status().isBadRequest());
        call(put(path + itemId).content("{\"result\":\"OK\",\"note\":\"%s\"}".formatted("n".repeat(301))),
                rui.token()).andExpect(status().isBadRequest());
        call(put(path + java.util.UUID.randomUUID()).content("{\"result\":\"OK\"}"), rui.token())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("checklist-item-not-found"));
        // An item of another order's checklist is not an item of this one.
        String otherStarted = call(post("/api/v1/work-orders/" + other + "/checklists/DELIVERY"), rui.token())
                .andReturn().getResponse().getContentAsString();
        call(put(path + JsonPath.<String>read(otherStarted, "$.items[0].id")).content("{\"result\":\"OK\"}"),
                rui.token()).andExpect(status().isNotFound());

        // Delivered and cancelled orders are closed.
        moveOk(g.owner(), other, "CANCELLED");
        call(post("/api/v1/work-orders/" + other + "/checklists/CHECK_IN"), g.owner())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("work-order-locked"));
        call(put("/api/v1/work-orders/" + other + "/checklists/DELIVERY/items/"
                + JsonPath.<String>read(otherStarted, "$.items[0].id")).content("{\"result\":\"OK\"}"), g.owner())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("work-order-locked"));
    }

    @Test
    void should_block_delivery_until_the_required_delivery_items_are_answered_when_the_workshop_requires_it()
            throws Exception {
        Shop g = garage("Lista Entrega");
        Mechanic rui = mechanic(g);
        String wo = orderOf(g, rui);
        moveOk(g.owner(), wo, "IN_PROGRESS");
        moveOk(g.owner(), wo, "READY");

        // The gate is off by default.
        String off = orderOf(g, rui);
        moveOk(g.owner(), off, "IN_PROGRESS");
        moveOk(g.owner(), off, "READY");
        moveOk(g.owner(), off, "DELIVERED");

        requireDeliveryChecklist(g, true);
        call(get("/api/v1/workshop/settings"), g.owner()).andExpect(jsonPath("$.deliveryChecklistRequired").value(true));
        call(get("/api/v1/work-orders/" + wo + "/checklists"), rui.token())
                .andExpect(jsonPath("$.deliveryChecklistRequired").value(true));

        // Not started: every required item of the template is open.
        move(g.owner(), wo, "DELIVERED")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("checklist-incomplete"));

        String started = call(post("/api/v1/work-orders/" + wo + "/checklists/DELIVERY"), g.owner())
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(started, "$.items[*].id");
        List<Boolean> required = JsonPath.read(started, "$.items[*].required");
        assertThat(required).containsExactly(true, true, true, true, false, false);
        String path = "/api/v1/work-orders/" + wo + "/checklists/DELIVERY/items/";
        for (int i = 0; i < 3; i++) {
            call(put(path + ids.get(i)).content("{\"result\":\"OK\"}"), rui.token()).andExpect(status().isOk());
        }
        move(g.owner(), wo, "DELIVERED")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("checklist-incomplete"));
        // Not applicable counts as answered; optional items do not matter.
        call(put(path + ids.get(3)).content("{\"result\":\"N_A\"}"), rui.token())
                .andExpect(jsonPath("$.complete").value(true));
        moveOk(g.owner(), wo, "DELIVERED");
        call(get("/api/v1/work-orders/" + wo), g.owner()).andExpect(jsonPath("$.status").value("DELIVERED"));
    }

    @Test
    void should_not_block_when_the_delivery_template_has_no_required_item_and_keep_illegal_moves_illegal()
            throws Exception {
        Shop g = garage("Lista Sem Obrigatorios");
        requireDeliveryChecklist(g, true);
        call(put("/api/v1/checklist-templates/DELIVERY").content("""
                {"items":[{"label":"Opcional","required":false}],"version":0}"""), g.owner())
                .andExpect(status().isOk());
        String wo = workOrder(g, vehicle(g, true), null);
        // DIAGNOSTICS cannot jump to DELIVERED: the usual error, not the gate.
        move(g.owner(), wo, "DELIVERED").andExpect(status().isConflict())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .doesNotContain("checklist-incomplete"));
        moveOk(g.owner(), wo, "IN_PROGRESS");
        moveOk(g.owner(), wo, "READY");
        moveOk(g.owner(), wo, "DELIVERED");
    }

    @Test
    void should_not_show_the_checklists_of_another_workshop() throws Exception {
        Shop a = garage("Lista A");
        Shop b = garage("Lista B");
        String wo = orderOf(a, mechanic(a));
        call(post("/api/v1/work-orders/" + wo + "/checklists/CHECK_IN"), a.owner()).andExpect(status().isCreated());
        call(get("/api/v1/work-orders/" + wo + "/checklists"), b.owner())
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("work-order-not-found"));
        call(post("/api/v1/work-orders/" + wo + "/checklists/CHECK_IN"), b.owner()).andExpect(status().isNotFound());
        // Templates are per workshop.
        call(put("/api/v1/checklist-templates/CHECK_IN").content(TWO_ITEMS.formatted(0)), a.owner())
                .andExpect(status().isOk());
        call(get("/api/v1/checklist-templates"), b.owner()).andExpect(jsonPath("$[0].custom").value(false));
    }
}

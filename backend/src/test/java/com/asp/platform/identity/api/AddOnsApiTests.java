package com.asp.platform.identity.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.asp.platform.ApiTestSupport;
import com.jayway.jsonpath.JsonPath;

/// ADR 0004: the platform admin switches online booking and the online shop on per workshop; the signed-in shell
/// reads them from `GET /branding`, the public branded login page does not see them.
class AddOnsApiTests extends ApiTestSupport {

    @Test
    void should_let_the_platform_admin_switch_add_ons_and_show_them_to_the_workshop() throws Exception {
        String body = provisionWorkshop("Oficina Extras", uniqueEmail("dono")).andReturn().getResponse()
                .getContentAsString();
        String id = JsonPath.read(body, "$.workshop.id");
        String slug = JsonPath.read(body, "$.workshop.slug");
        String owner = activate(JsonPath.read(body, "$.activationPath"), "Dono");
        String admin = platformAdmin();
        long version = versionAt(admin, path(id));

        call(get(path(id)), admin)
                .andExpect(jsonPath("$.addOns.booking").value(false))
                .andExpect(jsonPath("$.addOns.shop").value(false));
        call(get("/api/v1/branding"), owner).andExpect(jsonPath("$.addOns.booking").value(false));

        call(put(path(id) + "/add-ons").content(withVersion("{\"booking\":true,\"shop\":false}", version)), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addOns.booking").value(true))
                .andExpect(jsonPath("$.addOns.shop").value(false))
                .andExpect(jsonPath("$.version").value(version + 1));
        call(put(path(id) + "/add-ons").content(withVersion("{\"booking\":true,\"shop\":true}", version + 1)), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addOns.shop").value(true));

        call(get("/api/v1/admin/workshops"), admin)
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].addOns.booking").value(true));
        call(get("/api/v1/branding"), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addOns.booking").value(true))
                .andExpect(jsonPath("$.addOns.shop").value(true));
        // The public branded login says nothing about the subscription.
        mvc.perform(get("/api/v1/branding/" + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Oficina Extras"))
                .andExpect(jsonPath("$.addOns").doesNotExist());
    }

    @Test
    void should_refuse_a_stale_version_and_a_missing_flag() throws Exception {
        String id = JsonPath.read(provisionWorkshop("Oficina Extras Velha", uniqueEmail("dono"))
                .andReturn().getResponse().getContentAsString(), "$.workshop.id");
        String admin = platformAdmin();
        long version = versionAt(admin, path(id));
        call(put(path(id) + "/add-ons").content(withVersion("{\"booking\":true,\"shop\":false}", version)), admin)
                .andExpect(status().isOk());

        call(put(path(id) + "/add-ons").content(withVersion("{\"booking\":false,\"shop\":true}", version)), admin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("stale-data"));
        call(put(path(id) + "/add-ons").content(withVersion("{\"shop\":true}", version + 1)), admin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation"))
                .andExpect(jsonPath("$.errors.booking").exists());
        call(put(path(id) + "/add-ons").content("{\"booking\":true,\"shop\":true}"), admin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.version").exists());
        call(get(path(id)), admin).andExpect(jsonPath("$.addOns.booking").value(true))
                .andExpect(jsonPath("$.addOns.shop").value(false));
    }

    @Test
    void should_forbid_workshop_users_to_change_add_ons() throws Exception {
        String body = provisionWorkshop("Oficina Extras Proibida", uniqueEmail("dono")).andReturn().getResponse()
                .getContentAsString();
        String id = JsonPath.read(body, "$.workshop.id");
        String owner = activate(JsonPath.read(body, "$.activationPath"), "Dono");
        String frontDeskEmail = uniqueEmail("rececao");
        invite(owner, "FRONT_DESK", frontDeskEmail);
        String frontDesk = login(frontDeskEmail, PASSWORD);

        for (String token : new String[] {owner, frontDesk}) {
            call(put(path(id) + "/add-ons").content("{\"booking\":true,\"shop\":true,\"version\":0}"), token)
                    .andExpect(status().isForbidden());
        }
        call(get("/api/v1/branding"), owner).andExpect(jsonPath("$.addOns.booking").value(false));
    }

    private static String path(String id) {
        return "/api/v1/admin/workshops/" + id;
    }
}

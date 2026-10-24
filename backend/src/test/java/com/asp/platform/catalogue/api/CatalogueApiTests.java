package com.asp.platform.catalogue.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.asp.platform.ApiTestSupport;

/// The service catalogue: services and packages, who may see prices and change them, isolation between workshops,
/// and adding catalogue lines to a work order.
class CatalogueApiTests extends ApiTestSupport {

    private static final String OIL = """
            {"name":"Mudança de óleo","category":"Manutenção","standardHours":0.75,"priceCents":4000,"vatRate":23,
             "durationMinutes":60,"listedOnline":true}""";
    private static final String FILTER = """
            {"name":"Filtro de ar","standardHours":0.25,"priceCents":2000,"vatRate":23,"durationMinutes":30}""";
    private static final String INSPECTION = """
            {"name":"Inspeção geral","standardHours":0.5,"priceCents":1500,"vatRate":23,"durationMinutes":30}""";
    private static final String DIAGNOSTICS = """
            {"name":"Diagnóstico","standardHours":1.25,"pricing":"HOURLY","priceCents":0,"vatRate":23,
             "durationMinutes":90}""";

    @Test
    void should_let_the_owner_manage_services_and_hide_prices_from_mechanics() throws Exception {
        String owner = signup("Oficina Catálogo");
        String deskEmail = uniqueEmail("desk");
        invite(owner, "FRONT_DESK", deskEmail);
        String mechanicEmail = uniqueEmail("mec");
        invite(owner, "MECHANIC", mechanicEmail);

        String id = idOf(createService(owner, OIL)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Mudança de óleo"))
                .andExpect(jsonPath("$.priceCents").value(4000))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.listedOnline").value(true))
                .andExpect(jsonPath("$.version").value(0)));

        call(get("/api/v1/catalogue/services").param("q", "óleo"), login(deskEmail, PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].priceCents").value(4000));
        call(get("/api/v1/catalogue/services/" + id), login(mechanicEmail, PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Mudança de óleo"))
                .andExpect(jsonPath("$.standardHours").value(0.75))
                .andExpect(jsonPath("$.priceCents").doesNotExist())
                .andExpect(jsonPath("$.vatRate").doesNotExist());

        createService(login(deskEmail, PASSWORD), FILTER).andExpect(status().isForbidden());
        createService(login(mechanicEmail, PASSWORD), FILTER).andExpect(status().isForbidden());

        // Deactivated: kept, but no longer in the default list.
        call(put("/api/v1/catalogue/services/" + id).content(withVersion(OIL.replace("\"listedOnline\":true",
                "\"listedOnline\":true,\"active\":false"), 0)), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.version").value(1));
        call(get("/api/v1/catalogue/services"), owner).andExpect(jsonPath("$.length()").value(0));
        call(get("/api/v1/catalogue/services").param("activeOnly", "false"), owner)
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void should_validate_services_and_refuse_stale_edits() throws Exception {
        String owner = signup("Oficina Catálogo Regras");
        createService(owner, """
                {"name":" ","standardHours":-1,"priceCents":-5,"vatRate":101,"durationMinutes":2}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation"))
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.standardHours").exists())
                .andExpect(jsonPath("$.errors.priceCents").exists())
                .andExpect(jsonPath("$.errors.vatRate").exists())
                .andExpect(jsonPath("$.errors.durationMinutes").exists());

        String id = idOf(createService(owner, OIL));
        call(put("/api/v1/catalogue/services/" + id).content(OIL), owner)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.version").exists());
        String newPrice = OIL.replace("\"priceCents\":4000", "\"priceCents\":4500");
        call(put("/api/v1/catalogue/services/" + id).content(withVersion(newPrice, 0)), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        // A second editor still holding version 0 must not overwrite the new price.
        call(put("/api/v1/catalogue/services/" + id).content(withVersion(OIL, 0)), owner)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("stale-data"));
    }

    @Test
    void should_build_packages_from_active_services_of_the_same_workshop() throws Exception {
        String owner = signup("Oficina Pacotes");
        String oil = idOf(createService(owner, OIL));
        String filter = idOf(createService(owner, FILTER));
        String inspection = idOf(createService(owner, INSPECTION));

        String id = idOf(createPackage(owner, oil, filter, inspection, 6000)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priceCents").value(6000))
                .andExpect(jsonPath("$.listPriceCents").value(7500))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].name").value("Mudança de óleo")));

        createPackage(owner, oil, filter, "00000000-0000-0000-0000-000000000000", 1000)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("service-not-found"));
        call(post("/api/v1/catalogue/packages").content("""
                {"name":"Vazio","priceCents":0,"vatRate":23,"items":[]}"""), owner)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.items").exists());

        // A deactivated service cannot go into a new package.
        call(put("/api/v1/catalogue/services/" + inspection).content(withVersion(INSPECTION.replace(
                "\"durationMinutes\":30", "\"durationMinutes\":30,\"active\":false"), 0)), owner)
                .andExpect(status().isOk());
        createPackage(owner, oil, filter, inspection, 5000)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("service-inactive"));
        // ...but the existing package keeps it and can still be edited.
        call(put("/api/v1/catalogue/packages/" + id).content(withVersion(packageBody(oil, filter, inspection, 5500), 0)),
                owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceCents").value(5500))
                .andExpect(jsonPath("$.items[2].active").value(false));

        // Another workshop sees none of it and cannot use these services.
        String other = signup("Oficina Pacotes Intrusa");
        call(get("/api/v1/catalogue/packages/" + id), other).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("package-not-found"));
        call(get("/api/v1/catalogue/services/" + oil), other).andExpect(status().isNotFound());
        call(get("/api/v1/catalogue/packages"), other).andExpect(jsonPath("$.length()").value(0));
        createPackage(other, oil, filter, inspection, 100).andExpect(status().isNotFound());

        String mechanicEmail = uniqueEmail("mec");
        invite(owner, "MECHANIC", mechanicEmail);
        call(get("/api/v1/catalogue/packages"), login(mechanicEmail, PASSWORD)).andExpect(status().isForbidden());
    }

    @Test
    void should_add_a_service_or_a_package_to_a_work_order_at_the_catalogue_price() throws Exception {
        String owner = signup("Oficina Catálogo Ordem");
        String oil = idOf(createService(owner, OIL));
        String filter = idOf(createService(owner, FILTER));
        String inspection = idOf(createService(owner, INSPECTION));
        String pack = idOf(createPackage(owner, oil, filter, inspection, 6000));
        String woId = idOf(call(post("/api/v1/work-orders").content("{\"vehicleId\":\"%s\"}".formatted(
                createVehicle(owner, createCustomer(owner, "Cliente"), "12-CD-34"))), owner));

        addFromCatalogue(owner, woId, "{\"serviceId\":\"%s\"}".formatted(oil))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines.length()").value(1))
                .andExpect(jsonPath("$.lines[0].type").value("LABOR"))
                .andExpect(jsonPath("$.lines[0].description").value("Mudança de óleo"))
                .andExpect(jsonPath("$.lines[0].unitPriceCents").value(4000))
                .andExpect(jsonPath("$.netCents").value(4000));

        addFromCatalogue(owner, woId, "{\"packageId\":\"%s\"}".formatted(pack))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines.length()").value(4))
                .andExpect(jsonPath("$.lines[1].unitPriceCents").value(3200))
                .andExpect(jsonPath("$.lines[2].unitPriceCents").value(1600))
                .andExpect(jsonPath("$.lines[3].unitPriceCents").value(1200))
                .andExpect(jsonPath("$.netCents").value(10000))
                .andExpect(jsonPath("$.vatCents").value(2300));

        addFromCatalogue(owner, woId, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.serviceOrPackage").exists());
        addFromCatalogue(owner, woId, "{\"serviceId\":\"%s\",\"packageId\":\"%s\"}".formatted(oil, pack))
                .andExpect(status().isBadRequest());

        String other = signup("Oficina Catálogo Outra");
        String otherWo = idOf(call(post("/api/v1/work-orders").content("{\"vehicleId\":\"%s\"}".formatted(
                createVehicle(other, createCustomer(other, "Cliente"), "12-CD-35"))), other));
        addFromCatalogue(other, otherWo, "{\"serviceId\":\"%s\"}".formatted(oil))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("service-not-found"));
        addFromCatalogue(other, woId, "{\"serviceId\":\"%s\"}".formatted(oil)).andExpect(status().isNotFound());

        // Lines lock once the car is ready.
        call(patch("/api/v1/work-orders/" + woId + "/status").content("{\"status\":\"IN_PROGRESS\"}"), owner);
        call(patch("/api/v1/work-orders/" + woId + "/status").content("{\"status\":\"READY\"}"), owner);
        addFromCatalogue(owner, woId, "{\"serviceId\":\"%s\"}".formatted(oil))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("work-order-locked"));
    }

    @Test
    void should_refuse_an_inactive_service_on_a_work_order() throws Exception {
        String owner = signup("Oficina Catálogo Inativo");
        String id = idOf(createService(owner, OIL));
        call(put("/api/v1/catalogue/services/" + id).content(withVersion(OIL.replace("\"listedOnline\":true",
                "\"listedOnline\":true,\"active\":false"), 0)), owner).andExpect(status().isOk());
        String woId = idOf(call(post("/api/v1/work-orders").content("{\"vehicleId\":\"%s\"}".formatted(
                createVehicle(owner, createCustomer(owner, "Cliente"), "12-CD-36"))), owner));

        addFromCatalogue(owner, woId, "{\"serviceId\":\"%s\"}".formatted(id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("service-inactive"));
        String desk = uniqueEmail("desk");
        invite(owner, "MECHANIC", desk);
        addFromCatalogue(login(desk, PASSWORD), woId, "{\"serviceId\":\"%s\"}".formatted(id))
                .andExpect(status().isForbidden());
    }

    @Test
    void should_price_an_hourly_service_at_the_current_labour_rate() throws Exception {
        String owner = signup("Oficina Hora");
        setLabourRate(owner, 4010);
        String mechanicEmail = uniqueEmail("mec");
        invite(owner, "MECHANIC", mechanicEmail);

        // 1.25 h x 40.10 = 50.125, rounded half up to 50.13; the stored fixed price is ignored.
        String id = idOf(createService(owner, DIAGNOSTICS)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pricing").value("HOURLY"))
                .andExpect(jsonPath("$.priceCents").value(0))
                .andExpect(jsonPath("$.effectivePriceCents").value(5013)));
        String oil = idOf(createService(owner, OIL)
                .andExpect(jsonPath("$.pricing").value("FIXED"))
                .andExpect(jsonPath("$.effectivePriceCents").value(4000)));

        call(get("/api/v1/catalogue/services/" + id), login(mechanicEmail, PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pricing").value("HOURLY"))
                .andExpect(jsonPath("$.standardHours").value(1.25))
                .andExpect(jsonPath("$.effectivePriceCents").doesNotExist())
                .andExpect(jsonPath("$.priceCents").doesNotExist());

        // A new rate applies at once to hourly services, not to fixed ones.
        setLabourRate(owner, 4800);
        call(get("/api/v1/catalogue/services").param("activeOnly", "false"), owner)
                .andExpect(jsonPath("$[?(@.name == 'Diagnóstico')].effectivePriceCents").value(6000))
                .andExpect(jsonPath("$[?(@.name == 'Mudança de óleo')].effectivePriceCents").value(4000));

        // Switching back to a fixed price uses the stored price again.
        call(put("/api/v1/catalogue/services/" + id).content(withVersion(DIAGNOSTICS.replace("\"HOURLY\"", "\"FIXED\"")
                .replace("\"priceCents\":0", "\"priceCents\":3500"), 0)), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pricing").value("FIXED"))
                .andExpect(jsonPath("$.effectivePriceCents").value(3500));
        call(put("/api/v1/catalogue/services/" + oil).content(withVersion(OIL.replace("\"priceCents\":4000",
                "\"pricing\":\"BY_THE_MINUTE\",\"priceCents\":4000"), 0)), owner)
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_add_hourly_services_at_the_rate_of_the_moment_and_weigh_packages_by_effective_price()
            throws Exception {
        String owner = signup("Oficina Hora Ordem");
        setLabourRate(owner, 4000);
        String diagnostics = idOf(createService(owner, DIAGNOSTICS));
        String oil = idOf(createService(owner, OIL));
        String filter = idOf(createService(owner, FILTER));
        // 1.25 h x 40.00 = 50.00; with oil 40.00 and filter 20.00 the list price is 110.00.
        String pack = idOf(createPackage(owner, diagnostics, oil, filter, 8800)
                .andExpect(jsonPath("$.listPriceCents").value(11000))
                .andExpect(jsonPath("$.items[0].unitPriceCents").value(5000)));
        String woId = idOf(call(post("/api/v1/work-orders").content("{\"vehicleId\":\"%s\"}".formatted(
                createVehicle(owner, createCustomer(owner, "Cliente"), "12-CD-40"))), owner));

        addFromCatalogue(owner, woId, "{\"serviceId\":\"%s\"}".formatted(diagnostics))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines[0].unitPriceCents").value(5000))
                .andExpect(jsonPath("$.lines[0].serviceItemId").value(diagnostics));
        // 88.00 spread 50 : 40 : 20 = 40.00, 32.00, 16.00.
        addFromCatalogue(owner, woId, "{\"packageId\":\"%s\"}".formatted(pack))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines[1].unitPriceCents").value(4000))
                .andExpect(jsonPath("$.lines[2].unitPriceCents").value(3200))
                .andExpect(jsonPath("$.lines[3].unitPriceCents").value(1600))
                .andExpect(jsonPath("$.lines[3].serviceItemId").value(filter));

        // A later rate change does not touch the lines already on the order, only new ones.
        setLabourRate(owner, 6000);
        addFromCatalogue(owner, woId, "{\"serviceId\":\"%s\"}".formatted(diagnostics))
                .andExpect(jsonPath("$.lines[0].unitPriceCents").value(5000))
                .andExpect(jsonPath("$.lines[4].unitPriceCents").value(7500));
        // A line typed by hand has no catalogue service.
        call(post("/api/v1/work-orders/" + woId + "/lines").content("""
                {"type":"LABOR","description":"Extra","quantity":1,"unitPriceCents":1000,"vatRate":23}"""), owner)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines[5].serviceItemId").doesNotExist());
    }

    private void setLabourRate(String owner, long cents) throws Exception {
        call(put("/api/v1/workshop/settings").content(withVersion("""
                {"name":"Oficina","country":"PT","timezone":"Europe/Lisbon","defaultVatRate":23,
                 "labourRateCents":%d}""".formatted(cents), versionAt(owner, "/api/v1/workshop/settings"))), owner)
                .andExpect(status().isOk());
    }

    private ResultActions createService(String token, String body) throws Exception {
        return call(post("/api/v1/catalogue/services").content(body), token);
    }

    private ResultActions createPackage(String token, String a, String b, String c, long priceCents)
            throws Exception {
        return call(post("/api/v1/catalogue/packages").content(packageBody(a, b, c, priceCents)), token);
    }

    private static String packageBody(String a, String b, String c, long priceCents) {
        return """
                {"name":"Revisão anual","priceCents":%d,"vatRate":23,"listedOnline":true,
                 "items":[{"serviceId":"%s","quantity":1},{"serviceId":"%s","quantity":1},{"serviceId":"%s","quantity":1}]}"""
                .formatted(priceCents, a, b, c);
    }

    private ResultActions addFromCatalogue(String token, String woId, String body) throws Exception {
        return call(post("/api/v1/work-orders/" + woId + "/lines/from-catalogue").content(body), token);
    }
}

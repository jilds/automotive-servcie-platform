package com.asp.platform.catalogue.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.asp.platform.ApiTestSupport;
import com.jayway.jsonpath.JsonPath;

/// Parts inside service packages: a package holds services and stock products; on a work order the products become
/// PART lines that reserve stock, all or nothing, released when the line goes and consumed on delivery.
class PackagePartsApiTests extends ApiTestSupport {

    private static final String OIL_CHANGE = """
            {"name":"Mudança de óleo","standardHours":0.75,"priceCents":4000,"vatRate":23,"durationMinutes":60}""";
    private static final String FILTER = """
            {"sku":"OF-712","name":"Filtro de óleo","unit":"un","costCents":450,"salePriceCents":900,"vatRate":23}""";
    private static final String OIL = """
            {"sku":"5W30","name":"Óleo 5W30","unit":"l","costCents":600,"salePriceCents":1200,"vatRate":23}""";

    @Test
    void should_hold_services_and_products_and_price_the_list_with_both() throws Exception {
        String owner = signup("Oficina Pacote Peças");
        String service = idOf(call(post("/api/v1/catalogue/services").content(OIL_CHANGE), owner));
        String filter = product(owner, FILTER);
        String oil = product(owner, OIL);

        // 40.00 + 9.00 + 4 x 12.00 = 97.00 on their own.
        String id = idOf(createPackage(owner, service, filter, oil, 8000)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.listPriceCents").value(9700))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].kind").value("SERVICE"))
                .andExpect(jsonPath("$.items[0].serviceId").value(service))
                .andExpect(jsonPath("$.items[0].productId").doesNotExist())
                .andExpect(jsonPath("$.items[1].kind").value("PRODUCT"))
                .andExpect(jsonPath("$.items[1].productId").value(filter))
                .andExpect(jsonPath("$.items[1].serviceId").doesNotExist())
                .andExpect(jsonPath("$.items[1].name").value("Filtro de óleo"))
                .andExpect(jsonPath("$.items[1].sku").value("OF-712"))
                .andExpect(jsonPath("$.items[1].unitPriceCents").value(900))
                .andExpect(jsonPath("$.items[2].quantity").value(4))
                .andExpect(jsonPath("$.items[2].active").value(true)));
        call(get("/api/v1/catalogue/packages"), owner)
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].items[2].name").value("Óleo 5W30"));

        // Neither or both of serviceId and productId: a validation error on the item.
        call(post("/api/v1/catalogue/packages").content("""
                {"name":"Mau","priceCents":100,"vatRate":23,"items":[{"quantity":1}]}"""), owner)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation"));
        call(post("/api/v1/catalogue/packages").content("""
                {"name":"Mau","priceCents":100,"vatRate":23,
                 "items":[{"serviceId":"%s","productId":"%s","quantity":1}]}""".formatted(service, filter)), owner)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation"));
    }

    @Test
    void should_reserve_the_parts_of_a_package_release_them_with_the_line_and_consume_them_on_delivery()
            throws Exception {
        String owner = signup("Oficina Pacote Reserva");
        String service = idOf(call(post("/api/v1/catalogue/services").content(OIL_CHANGE), owner));
        String filter = product(owner, FILTER);
        String oil = product(owner, OIL);
        adjust(owner, filter, 5);
        adjust(owner, oil, 10);
        String pack = idOf(createPackage(owner, service, filter, oil, 8000));
        String wo = openWorkOrder(owner, "12-PK-01");

        // 80.00 in proportion 40 : 9 : 48, the oil's share in whole cents per litre, the rest on the filter:
        // 32.99 + 7.41 + 4 x 9.90 = 80.00.
        String body = addPackage(owner, wo, pack)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines.length()").value(3))
                .andExpect(jsonPath("$.lines[0].type").value("LABOR"))
                .andExpect(jsonPath("$.lines[0].serviceItemId").value(service))
                .andExpect(jsonPath("$.lines[0].unitPriceCents").value(3299))
                .andExpect(jsonPath("$.lines[1].type").value("PART"))
                .andExpect(jsonPath("$.lines[1].productId").value(filter))
                .andExpect(jsonPath("$.lines[1].serviceItemId").doesNotExist())
                .andExpect(jsonPath("$.lines[1].description").value("Revisão com peças: Filtro de óleo (OF-712)"))
                .andExpect(jsonPath("$.lines[1].quantity").value(1))
                .andExpect(jsonPath("$.lines[1].unitPriceCents").value(741))
                .andExpect(jsonPath("$.lines[2].productId").value(oil))
                .andExpect(jsonPath("$.lines[2].quantity").value(4))
                .andExpect(jsonPath("$.lines[2].unitPriceCents").value(990))
                .andExpect(jsonPath("$.netCents").value(8000))
                .andReturn().getResponse().getContentAsString();
        stock(owner, filter).andExpect(jsonPath("$.reserved").value(1)).andExpect(jsonPath("$.available").value(4));
        stock(owner, oil).andExpect(jsonPath("$.reserved").value(4)).andExpect(jsonPath("$.available").value(6));

        // Removing the oil line gives the oil back; the filter stays reserved.
        String oilLine = JsonPath.read(body, "$.lines[2].id");
        call(delete("/api/v1/work-orders/" + wo + "/lines/" + oilLine), owner).andExpect(status().isOk());
        stock(owner, oil).andExpect(jsonPath("$.reserved").value(0));
        stock(owner, filter).andExpect(jsonPath("$.reserved").value(1));

        // Delivering takes the filter out of stock.
        move(owner, wo, "IN_PROGRESS");
        move(owner, wo, "READY");
        move(owner, wo, "DELIVERED").andExpect(status().isOk());
        stock(owner, filter).andExpect(jsonPath("$.onHand").value(4)).andExpect(jsonPath("$.reserved").value(0));
        stock(owner, oil).andExpect(jsonPath("$.onHand").value(10));
    }

    @Test
    void should_add_nothing_when_a_part_of_the_package_is_short() throws Exception {
        String owner = signup("Oficina Pacote Falta");
        String service = idOf(call(post("/api/v1/catalogue/services").content(OIL_CHANGE), owner));
        String filter = product(owner, FILTER);
        String oil = product(owner, OIL);
        adjust(owner, filter, 5);
        adjust(owner, oil, 3);
        String pack = idOf(createPackage(owner, service, filter, oil, 8000));
        String wo = openWorkOrder(owner, "12-PK-02");

        addPackage(owner, wo, pack)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("insufficient-stock"));
        call(get("/api/v1/work-orders/" + wo), owner).andExpect(jsonPath("$.lines.length()").value(0));
        stock(owner, filter).andExpect(jsonPath("$.reserved").value(0));
        stock(owner, oil).andExpect(jsonPath("$.reserved").value(0));
    }

    @Test
    void should_refuse_products_of_another_workshop_and_products_no_longer_sold() throws Exception {
        String owner = signup("Oficina Pacote Regras");
        String service = idOf(call(post("/api/v1/catalogue/services").content(OIL_CHANGE), owner));
        String filter = product(owner, FILTER);
        String oil = product(owner, OIL);
        String other = signup("Oficina Pacote Intrusa");
        String foreign = product(other, OIL);

        createPackage(owner, service, filter, foreign, 8000)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("product-not-found"));

        String pack = idOf(createPackage(owner, service, filter, oil, 8000));
        // The oil is no longer sold: it cannot go into a new package...
        call(put("/api/v1/stock/products/" + oil).content(withVersion(OIL.replace("\"vatRate\":23",
                "\"vatRate\":23,\"active\":false"), 0)), owner).andExpect(status().isOk());
        createPackage(owner, service, filter, oil, 7000)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("product-inactive"));
        // ...the package that holds it keeps it, shows it as inactive and can still be edited...
        call(put("/api/v1/catalogue/packages/" + pack).content(withVersion(packageBody(service, filter, oil, 7500), 0)),
                owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceCents").value(7500))
                .andExpect(jsonPath("$.items[2].active").value(false));
        // ...but cannot go onto a work order until the product is sold again.
        adjust(owner, filter, 5);
        String wo = openWorkOrder(owner, "12-PK-03");
        addPackage(owner, wo, pack)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("product-inactive"));
        call(get("/api/v1/work-orders/" + wo), owner).andExpect(jsonPath("$.lines.length()").value(0));

        // The other workshop cannot add this package to its own work order.
        addPackage(other, openWorkOrder(other, "12-PK-04"), pack)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("package-not-found"));
    }

    private String product(String token, String body) throws Exception {
        return idOf(call(post("/api/v1/stock/products").content(body), token).andExpect(status().isCreated()));
    }

    private void adjust(String token, String productId, int quantity) throws Exception {
        call(post("/api/v1/stock/products/" + productId + "/adjustments").content(
                "{\"quantity\":%d,\"reason\":\"Contagem\"}".formatted(quantity)), token)
                .andExpect(status().isCreated());
    }

    private ResultActions stock(String token, String productId) throws Exception {
        return call(get("/api/v1/stock/products/" + productId), token).andExpect(status().isOk());
    }

    private ResultActions createPackage(String token, String service, String product1, String product2,
            long priceCents) throws Exception {
        return call(post("/api/v1/catalogue/packages").content(packageBody(service, product1, product2, priceCents)),
                token);
    }

    /// One service, one unit of the first product and four of the second.
    private static String packageBody(String service, String product1, String product2, long priceCents) {
        return """
                {"name":"Revisão com peças","priceCents":%d,"vatRate":23,
                 "items":[{"serviceId":"%s","quantity":1},{"productId":"%s","quantity":1},
                          {"productId":"%s","quantity":4}]}"""
                .formatted(priceCents, service, product1, product2);
    }

    private ResultActions addPackage(String token, String workOrderId, String packageId) throws Exception {
        return call(post("/api/v1/work-orders/" + workOrderId + "/lines/from-catalogue").content(
                "{\"packageId\":\"%s\"}".formatted(packageId)), token);
    }

    private ResultActions move(String token, String workOrderId, String status) throws Exception {
        return call(patch("/api/v1/work-orders/" + workOrderId + "/status").content(
                "{\"status\":\"%s\"}".formatted(status)), token);
    }

    private String openWorkOrder(String token, String plate) throws Exception {
        return idOf(call(post("/api/v1/work-orders").content("{\"vehicleId\":\"%s\"}".formatted(
                createVehicle(token, createCustomer(token, "Cliente"), plate))), token)
                .andExpect(status().isCreated()));
    }
}

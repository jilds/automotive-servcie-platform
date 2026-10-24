package com.asp.platform.stock.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;

import com.asp.platform.ApiTestSupport;
import com.jayway.jsonpath.JsonPath;

/// Stock over the API: products and who sees what, adjustments and the ledger, purchase orders, isolation between
/// workshops, and part lines from stock on work orders (reserve, release, consume, never oversell).
class StockApiTests extends ApiTestSupport {

    private static final String FILTER = """
            {"sku":"OF-712","name":"Filtro de óleo","brand":"Mann","unit":"un","costCents":450,"salePriceCents":900,
             "vatRate":23,"minStock":2,"bin":"A-03"}""";
    private static final String PADS = """
            {"sku":"BP-100","name":"Pastilhas de travão","costCents":2000,"salePriceCents":3500,"vatRate":23}""";

    @Test
    void should_let_the_owner_manage_products_and_show_mechanics_no_cost_or_prices() throws Exception {
        String owner = signup("Oficina Stock");
        String deskEmail = uniqueEmail("desk");
        invite(owner, "FRONT_DESK", deskEmail);
        String mechanicEmail = uniqueEmail("mec");
        invite(owner, "MECHANIC", mechanicEmail);
        String desk = login(deskEmail, PASSWORD);
        String mechanic = login(mechanicEmail, PASSWORD);

        String id = idOf(createProduct(owner, FILTER)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("OF-712"))
                .andExpect(jsonPath("$.costCents").value(450))
                .andExpect(jsonPath("$.salePriceCents").value(900))
                .andExpect(jsonPath("$.onHand").value(0))
                .andExpect(jsonPath("$.available").value(0))
                .andExpect(jsonPath("$.low").value(true))
                .andExpect(jsonPath("$.unit").value("un"))
                .andExpect(jsonPath("$.version").value(0)));
        createProduct(owner, PADS).andExpect(status().isCreated()).andExpect(jsonPath("$.unit").value("un"));

        call(get("/api/v1/stock/products").param("q", "óleo"), desk)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].costCents").value(450));
        call(get("/api/v1/stock/products/" + id), mechanic)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Filtro de óleo"))
                .andExpect(jsonPath("$.sku").value("OF-712"))
                .andExpect(jsonPath("$.bin").value("A-03"))
                .andExpect(jsonPath("$.available").value(0))
                .andExpect(jsonPath("$.costCents").doesNotExist())
                .andExpect(jsonPath("$.salePriceCents").doesNotExist())
                .andExpect(jsonPath("$.vatRate").doesNotExist())
                .andExpect(jsonPath("$.onHand").doesNotExist());
        call(get("/api/v1/stock/products"), mechanic)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].salePriceCents").doesNotExist());

        createProduct(desk, PADS.replace("BP-100", "BP-200")).andExpect(status().isForbidden());
        createProduct(mechanic, PADS.replace("BP-100", "BP-200")).andExpect(status().isForbidden());
        call(get("/api/v1/stock/products/" + id + "/movements"), mechanic).andExpect(status().isForbidden());
        adjust(mechanic, id, "1", "Contagem").andExpect(status().isForbidden());
        call(get("/api/v1/stock/purchase-orders"), mechanic).andExpect(status().isForbidden());
        call(get("/api/v1/stock/low-stock"), mechanic).andExpect(status().isForbidden());

        // Deactivated: kept, but no longer in the default list.
        call(put("/api/v1/stock/products/" + id).content(withVersion(FILTER.replace("\"bin\":\"A-03\"",
                "\"bin\":\"A-04\",\"active\":false"), 0)), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.bin").value("A-04"))
                .andExpect(jsonPath("$.version").value(1));
        call(get("/api/v1/stock/products"), owner).andExpect(jsonPath("$.length()").value(1));
        call(get("/api/v1/stock/products").param("activeOnly", "false"), owner)
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void should_validate_products_refuse_duplicate_skus_and_stale_edits() throws Exception {
        String owner = signup("Oficina Stock Regras");
        createProduct(owner, """
                {"sku":" ","name":"","costCents":-1,"salePriceCents":-1,"vatRate":101,"minStock":-1}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation"))
                .andExpect(jsonPath("$.errors.sku").exists())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.costCents").exists())
                .andExpect(jsonPath("$.errors.salePriceCents").exists())
                .andExpect(jsonPath("$.errors.vatRate").exists())
                .andExpect(jsonPath("$.errors.minStock").exists());

        String id = idOf(createProduct(owner, FILTER));
        createProduct(owner, PADS.replace("BP-100", "of-712"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("duplicate-sku"));

        call(put("/api/v1/stock/products/" + id).content(FILTER), owner)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.version").exists());
        // Stock movements do not change the version: an edit dialog stays valid while parts come and go.
        adjust(owner, id, "5", "Stock inicial").andExpect(status().isCreated());
        String newPrice = FILTER.replace("\"salePriceCents\":900", "\"salePriceCents\":950");
        call(put("/api/v1/stock/products/" + id).content(withVersion(newPrice, 0)), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salePriceCents").value(950))
                .andExpect(jsonPath("$.onHand").value(5))
                .andExpect(jsonPath("$.version").value(1));
        call(put("/api/v1/stock/products/" + id).content(withVersion(FILTER, 0)), owner)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("stale-data"));
        String pads = idOf(createProduct(owner, PADS));
        call(put("/api/v1/stock/products/" + pads).content(withVersion(PADS.replace("BP-100", "OF-712"), 0)), owner)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("duplicate-sku"));
    }

    @Test
    void should_adjust_stock_with_a_reason_and_keep_the_ledger() throws Exception {
        String owner = signup("Oficina Stock Ajustes");
        String deskEmail = uniqueEmail("desk");
        invite(owner, "FRONT_DESK", deskEmail);
        String desk = login(deskEmail, PASSWORD);
        String id = idOf(createProduct(owner, FILTER));

        adjust(desk, id, "3", "Contagem inicial")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.onHand").value(3))
                .andExpect(jsonPath("$.available").value(3))
                .andExpect(jsonPath("$.low").value(false));
        adjust(owner, id, "-1.5", "Partido na montagem")
                .andExpect(jsonPath("$.onHand").value(1.5))
                .andExpect(jsonPath("$.low").value(true));
        adjust(owner, id, "-2", "Perdido")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("insufficient-stock"));
        adjust(owner, id, "1", " ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("adjustment-reason-required"))
                .andExpect(jsonPath("$.errors.reason").exists());
        call(post("/api/v1/stock/products/" + id + "/adjustments").content("{\"quantity\":0,\"reason\":\"x\"}"), owner)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.change").exists());
        adjust(owner, id, "0.0001", "Demasiadas casas")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.quantity").exists());

        call(get("/api/v1/stock/products/" + id + "/movements"), desk)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value("ADJUSTMENT"))
                .andExpect(jsonPath("$[0].quantity").value(-1.5))
                .andExpect(jsonPath("$[0].reason").value("Partido na montagem"))
                .andExpect(jsonPath("$[0].changedByName").value("Owner"))
                .andExpect(jsonPath("$[1].quantity").value(3))
                .andExpect(jsonPath("$[1].changedByName").value("front_desk"));

        call(get("/api/v1/stock/low-stock"), owner)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id));
        call(get("/api/v1/stock/products").param("lowOnly", "true"), owner)
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void should_receive_a_purchase_order_into_stock_once() throws Exception {
        String owner = signup("Oficina Encomendas");
        String filter = idOf(createProduct(owner, FILTER));
        String pads = idOf(createProduct(owner, PADS));

        String body = poBody("Auto Peças Lda", filter, "4", 450, pads, "2", 1999);
        String id = idOf(call(post("/api/v1/stock/purchase-orders").content(body), owner)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.totalCents").value(5798))
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andExpect(jsonPath("$.lines[0].sku").value("OF-712"))
                .andExpect(jsonPath("$.lines[1].name").value("Pastilhas de travão")));

        String changed = poBody("Auto Peças Norte", filter, "6", 450, pads, "2", 1999);
        call(put("/api/v1/stock/purchase-orders/" + id).content(withVersion(changed, 0)), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supplier").value("Auto Peças Norte"))
                .andExpect(jsonPath("$.lines[0].quantity").value(6))
                .andExpect(jsonPath("$.version").value(1));
        call(put("/api/v1/stock/purchase-orders/" + id).content(withVersion(body, 0)), owner)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("stale-data"));
        call(post("/api/v1/stock/purchase-orders/" + id + "/receive"), owner)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("illegal-purchase-order-status"));

        call(post("/api/v1/stock/purchase-orders/" + id + "/order"), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ORDERED"))
                .andExpect(jsonPath("$.orderedAt").exists());
        call(put("/api/v1/stock/purchase-orders/" + id).content(withVersion(changed, 2)), owner)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("purchase-order-locked"));

        call(post("/api/v1/stock/purchase-orders/" + id + "/receive"), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.receivedAt").exists());
        call(get("/api/v1/stock/products/" + filter), owner).andExpect(jsonPath("$.onHand").value(6));
        call(get("/api/v1/stock/products/" + pads), owner).andExpect(jsonPath("$.onHand").value(2));
        call(get("/api/v1/stock/products/" + filter + "/movements"), owner)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("RECEIPT"))
                .andExpect(jsonPath("$[0].referenceType").value("PURCHASE_ORDER"))
                .andExpect(jsonPath("$[0].referenceId").value(id));

        call(post("/api/v1/stock/purchase-orders/" + id + "/receive"), owner)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("illegal-purchase-order-status"));
        call(get("/api/v1/stock/products/" + filter), owner).andExpect(jsonPath("$.onHand").value(6));
        call(get("/api/v1/stock/purchase-orders").param("status", "RECEIVED"), owner)
                .andExpect(jsonPath("$.length()").value(1));
        call(get("/api/v1/stock/purchase-orders").param("status", "DRAFT"), owner)
                .andExpect(jsonPath("$.length()").value(0));

        call(post("/api/v1/stock/purchase-orders").content("{\"supplier\":\"X\",\"lines\":[]}"), owner)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.lines").exists());
    }

    @Test
    void should_keep_products_movements_reservations_and_purchase_orders_inside_the_workshop() throws Exception {
        String owner = signup("Oficina Stock Dona");
        String filter = idOf(createProduct(owner, FILTER));
        adjust(owner, filter, "2", "Contagem");
        String po = idOf(call(post("/api/v1/stock/purchase-orders").content(
                poBody("Fornecedor", filter, "1", 450, null, null, 0)), owner));
        String wo = openWorkOrder(owner, "12-AB-34");
        addFromStock(owner, wo, filter, "1").andExpect(status().isCreated());

        String other = signup("Oficina Stock Intrusa");
        call(get("/api/v1/stock/products/" + filter), other)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("product-not-found"));
        call(get("/api/v1/stock/products"), other).andExpect(jsonPath("$.length()").value(0));
        call(get("/api/v1/stock/products/" + filter + "/movements"), other).andExpect(status().isNotFound());
        adjust(other, filter, "5", "Roubo").andExpect(status().isNotFound());
        call(put("/api/v1/stock/products/" + filter).content(withVersion(FILTER, 0)), other)
                .andExpect(status().isNotFound());
        call(get("/api/v1/stock/purchase-orders/" + po), other)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("purchase-order-not-found"));
        call(get("/api/v1/stock/purchase-orders"), other).andExpect(jsonPath("$.length()").value(0));
        call(post("/api/v1/stock/purchase-orders/" + po + "/order"), other).andExpect(status().isNotFound());
        call(post("/api/v1/stock/purchase-orders").content(poBody("Fornecedor", filter, "1", 1, null, null, 0)), other)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("product-not-found"));
        // Another workshop's part cannot go on (or be reserved for) its work orders.
        String otherWo = openWorkOrder(other, "12-AB-35");
        addFromStock(other, otherWo, filter, "1")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("product-not-found"));
        addFromStock(other, wo, filter, "1").andExpect(status().isNotFound());

        // The owner's reservation is untouched.
        call(get("/api/v1/stock/products/" + filter), owner)
                .andExpect(jsonPath("$.reserved").value(1))
                .andExpect(jsonPath("$.available").value(1));
    }

    @Test
    void should_reserve_part_lines_release_them_and_consume_them_on_delivery() throws Exception {
        String owner = signup("Oficina Stock Ordens");
        String mechanicEmail = uniqueEmail("mec");
        String mechanicId = invite(owner, "MECHANIC", mechanicEmail);
        String filter = idOf(createProduct(owner, FILTER));
        adjust(owner, filter, "5", "Contagem");
        String wo = openWorkOrder(owner, "12-CD-34");

        String body = addFromStock(owner, wo, filter, "2")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines.length()").value(1))
                .andExpect(jsonPath("$.lines[0].type").value("PART"))
                .andExpect(jsonPath("$.lines[0].description").value("Filtro de óleo (OF-712)"))
                .andExpect(jsonPath("$.lines[0].unitPriceCents").value(900))
                .andExpect(jsonPath("$.lines[0].vatRate").value(23))
                .andExpect(jsonPath("$.lines[0].productId").value(filter))
                .andExpect(jsonPath("$.netCents").value(1800))
                .andReturn().getResponse().getContentAsString();
        String lineId = JsonPath.read(body, "$.lines[0].id");
        stock(owner, filter).andExpect(jsonPath("$.onHand").value(5)).andExpect(jsonPath("$.reserved").value(2))
                .andExpect(jsonPath("$.available").value(3));

        addFromStock(owner, wo, filter, "4")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("insufficient-stock"));
        call(get("/api/v1/work-orders/" + wo), owner).andExpect(jsonPath("$.lines.length()").value(1));
        addFromStock(owner, wo, filter, "0")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.quantity").exists());

        // Removing the line gives the parts back.
        call(delete("/api/v1/work-orders/" + wo + "/lines/" + lineId), owner).andExpect(status().isOk());
        stock(owner, filter).andExpect(jsonPath("$.reserved").value(0)).andExpect(jsonPath("$.available").value(5));

        // Cancelling the order gives them back too.
        addFromStock(owner, wo, filter, "3").andExpect(status().isCreated());
        stock(owner, filter).andExpect(jsonPath("$.available").value(2));
        move(owner, wo, "CANCELLED").andExpect(status().isOk());
        stock(owner, filter).andExpect(jsonPath("$.reserved").value(0)).andExpect(jsonPath("$.onHand").value(5));

        // Delivering takes them out of stock, with an ISSUE movement that points at the work order.
        String delivered = openWorkOrder(owner, "12-CD-35", mechanicId);
        addFromStock(owner, delivered, filter, "2").andExpect(status().isCreated());
        call(get("/api/v1/work-orders/" + delivered), login(mechanicEmail, PASSWORD))
                .andExpect(jsonPath("$.lines[0].description").value("Filtro de óleo (OF-712)"))
                .andExpect(jsonPath("$.lines[0].unitPriceCents").doesNotExist());
        move(owner, delivered, "IN_PROGRESS");
        move(owner, delivered, "READY");
        stock(owner, filter).andExpect(jsonPath("$.onHand").value(5)).andExpect(jsonPath("$.reserved").value(2));
        move(owner, delivered, "DELIVERED").andExpect(status().isOk());
        stock(owner, filter).andExpect(jsonPath("$.onHand").value(3)).andExpect(jsonPath("$.reserved").value(0))
                .andExpect(jsonPath("$.available").value(3));
        call(get("/api/v1/stock/products/" + filter + "/movements"), owner)
                .andExpect(jsonPath("$[0].type").value("ISSUE"))
                .andExpect(jsonPath("$[0].quantity").value(-2))
                .andExpect(jsonPath("$[0].referenceType").value("WORK_ORDER"))
                .andExpect(jsonPath("$[0].referenceId").value(delivered));

        // An inactive product cannot go on a work order.
        call(put("/api/v1/stock/products/" + filter).content(withVersion(FILTER.replace("\"bin\":\"A-03\"",
                "\"bin\":\"A-03\",\"active\":false"), 0)), owner).andExpect(status().isOk());
        addFromStock(owner, openWorkOrder(owner, "12-CD-36"), filter, "1")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("product-inactive"));
        addFromStock(login(mechanicEmail, PASSWORD), delivered, filter, "1").andExpect(status().isForbidden());
    }

    @Test
    void should_let_only_one_of_two_simultaneous_reservations_take_the_last_unit() throws Exception {
        String owner = signup("Oficina Stock Corrida");
        String filter = idOf(createProduct(owner, FILTER));
        adjust(owner, filter, "1", "Última unidade");
        List<String> orders = List.of(openWorkOrder(owner, "12-EF-34"), openWorkOrder(owner, "12-EF-35"));

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<MockHttpServletResponse>> results = new ArrayList<>();
            for (String wo : orders) {
                Callable<MockHttpServletResponse> race = () -> {
                    start.await();
                    return addFromStock(owner, wo, filter, "1").andReturn().getResponse();
                };
                results.add(pool.submit(race));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            List<String> bodies = new ArrayList<>();
            for (Future<MockHttpServletResponse> result : results) {
                MockHttpServletResponse response = result.get();
                statuses.add(response.getStatus());
                bodies.add(response.getContentAsString());
            }
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
            assertThat(bodies.get(statuses.indexOf(409))).contains("\"insufficient-stock\"");
        } finally {
            pool.shutdownNow();
        }
        stock(owner, filter).andExpect(jsonPath("$.reserved").value(1)).andExpect(jsonPath("$.available").value(0));
    }

    private ResultActions createProduct(String token, String body) throws Exception {
        return call(post("/api/v1/stock/products").content(body), token);
    }

    private ResultActions adjust(String token, String productId, String quantity, String reason) throws Exception {
        return call(post("/api/v1/stock/products/" + productId + "/adjustments").content(
                "{\"quantity\":%s,\"reason\":\"%s\"}".formatted(quantity, reason)), token);
    }

    private ResultActions stock(String token, String productId) throws Exception {
        return call(get("/api/v1/stock/products/" + productId), token).andExpect(status().isOk());
    }

    private ResultActions addFromStock(String token, String workOrderId, String productId, String quantity)
            throws Exception {
        return call(post("/api/v1/work-orders/" + workOrderId + "/lines/from-stock").content(
                "{\"productId\":\"%s\",\"quantity\":%s}".formatted(productId, quantity)), token);
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

    private String openWorkOrder(String token, String plate, String mechanicId) throws Exception {
        return idOf(call(post("/api/v1/work-orders").content("{\"vehicleId\":\"%s\",\"assignedMechanicId\":\"%s\"}"
                .formatted(createVehicle(token, createCustomer(token, "Cliente"), plate), mechanicId)), token)
                .andExpect(status().isCreated()));
    }

    private static String poBody(String supplier, String product1, String qty1, long cost1,
            @Nullable String product2, @Nullable String qty2, long cost2) {
        String lines = "{\"productId\":\"%s\",\"quantity\":%s,\"unitCostCents\":%d}".formatted(product1, qty1, cost1);
        if (product2 != null) {
            lines += ",{\"productId\":\"%s\",\"quantity\":%s,\"unitCostCents\":%d}".formatted(product2, qty2, cost2);
        }
        return "{\"supplier\":\"%s\",\"lines\":[%s]}".formatted(supplier, lines);
    }
}

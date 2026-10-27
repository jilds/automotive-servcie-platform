package com.asp.platform.shop;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.test.web.servlet.ResultActions;

import com.asp.platform.booking.BookingTestSupport;
import com.jayway.jsonpath.JsonPath;

/// Workshops with the shop add-on on (switched by the platform admin), products listed online with stock, and
/// orders placed on the public page. Public requests come from a new random client address each time (see
/// [BookingTestSupport]).
public abstract class ShopTestSupport extends BookingTestSupport {

    protected record Shop(String id, String slug, String owner) {

        public String publicPath(String path) {
            return "/api/v1/public/" + slug + "/shop" + path;
        }
    }

    /// A Portuguese workshop with the shop add-on on (when `shop`), its owner signed in.
    protected Shop shop(String name, boolean shop) throws Exception {
        String body = provisionWorkshop(name, uniqueEmail("dono")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String owner = activate(JsonPath.read(body, "$.activationPath"), "Owner");
        Shop s = new Shop(JsonPath.read(body, "$.workshop.id"), JsonPath.read(body, "$.workshop.slug"), owner);
        if (shop) {
            String admin = platformAdmin();
            long version = versionAt(admin, "/api/v1/admin/workshops/" + s.id());
            call(put("/api/v1/admin/workshops/" + s.id() + "/add-ons")
                    .content(withVersion("{\"booking\":false,\"shop\":true}", version)), admin)
                    .andExpect(status().isOk());
        }
        return s;
    }

    /// Invites a team member with this role and returns their token.
    protected String member(Shop s, String role) throws Exception {
        String email = uniqueEmail(role.toLowerCase(java.util.Locale.ROOT));
        invite(s.owner(), role, email);
        return login(email, PASSWORD);
    }

    /// A product at 23 % VAT with `onHand` units in stock; returns its id.
    protected String product(Shop s, String sku, String name, long salePriceCents, boolean listedOnline,
            int onHand) throws Exception {
        String id = idOf(call(post("/api/v1/stock/products").content("""
                {"sku":"%s","name":"%s","brand":"Bosch","unit":"un","costCents":500,"salePriceCents":%d,
                 "vatRate":23,"minStock":0,"active":true,"listedOnline":%s}"""
                .formatted(sku, name, salePriceCents, listedOnline)), s.owner()).andExpect(status().isCreated()));
        if (onHand > 0) {
            call(post("/api/v1/stock/products/" + id + "/adjustments")
                    .content("{\"quantity\":%d,\"reason\":\"Stock inicial\"}".formatted(onHand)), s.owner())
                    .andExpect(status().isCreated());
        }
        return id;
    }

    /// `POST /orders` from a new client address.
    protected ResultActions order(Shop s, String body) throws Exception {
        return publicCall(post(s.publicPath("/orders")).content(body));
    }

    /// A valid order body; `lines` alternates product ids and quantities.
    protected static String orderBody(Object... lines) {
        String json = IntStream.range(0, lines.length / 2)
                .mapToObj(i -> "{\"productId\":\"%s\",\"quantity\":%s}".formatted(lines[2 * i], lines[2 * i + 1]))
                .collect(Collectors.joining(","));
        return """
                {"lines":[%s],"name":"Ana Silva","phone":"912345678","email":"ana@example.pt","nif":"123456789",
                 "privacyAccepted":true,"marketingConsent":false,"website":"","language":"pt-PT"}""".formatted(json);
    }

    /// Places the order and returns the status token.
    protected String placeAndGetToken(Shop s, Object... lines) throws Exception {
        return JsonPath.read(order(s, orderBody(lines)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.statusToken");
    }

    /// The id of the order with this number, as the front desk lists it.
    protected String orderId(Shop s, int number) throws Exception {
        List<String> ids = JsonPath.read(call(get("/api/v1/shop-orders"), s.owner()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$[?(@.number == " + number + ")].id");
        return ids.getFirst();
    }

    /// `available` of a product as the owner sees it on the stock screens.
    protected double available(Shop s, String productId) throws Exception {
        return stockFigure(s, productId, "$.available");
    }

    protected double onHand(Shop s, String productId) throws Exception {
        return stockFigure(s, productId, "$.onHand");
    }

    private double stockFigure(Shop s, String productId, String path) throws Exception {
        Number n = JsonPath.read(call(get("/api/v1/stock/products/" + productId), s.owner())
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), path);
        return n.doubleValue();
    }
}

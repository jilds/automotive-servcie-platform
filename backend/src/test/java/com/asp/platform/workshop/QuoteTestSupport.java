package com.asp.platform.workshop;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import com.asp.platform.shop.ShopTestSupport;
import com.jayway.jsonpath.JsonPath;

/// Workshops (no add-ons) with a customer, a vehicle and a work order with lines, quotes sent by the owner, and the
/// customer's link. Direct SQL runs as the app role (`asp_app`, row-level security on) for the given workshop.
public abstract class QuoteTestSupport extends ShopTestSupport {

    protected static final String PLATE = "AA-00-AA";

    private static final String OWNER_ROLE = "asp_owner";

    @Autowired
    protected JdbcTemplate jdbc;

    @Value("${spring.datasource.url}")
    private String jdbcUrl;

    /// A workshop with its owner signed in; no add-ons, quotes are a core feature.
    protected Shop garage(String name) throws Exception {
        return shop(name, false);
    }

    /// Vehicles registered per workshop, for unique plates: the first is [#PLATE], then AA-01-AA, …
    private final Map<String, AtomicInteger> vehicles = new ConcurrentHashMap<>();

    /// A customer (with or without an e-mail) and their vehicle; returns the vehicle id.
    protected String vehicle(Shop g, boolean withEmail) throws Exception {
        return vehicle(g, withEmail ? "ana@cliente.pt" : null);
    }

    /// A customer with this e-mail (or none) and their vehicle; returns the vehicle id.
    protected String vehicle(Shop g, @Nullable String customerEmail) throws Exception {
        String email = customerEmail == null ? "" : ",\"email\":\"" + customerEmail + "\"";
        String customer = idOf(call(post("/api/v1/customers").content("""
                {"name":"Ana Pereira","nif":"123456789","phone":"+351912345678"%s}""".formatted(email)), g.owner())
                .andExpect(status().isCreated()));
        int n = vehicles.computeIfAbsent(g.id(), id -> new AtomicInteger()).getAndIncrement();
        return createVehicle(g.owner(), customer, "AA-%02d-AA".formatted(n));
    }

    /// Checks the vehicle in (DIAGNOSTICS), with an optional mechanic; returns the work order id.
    protected String workOrder(Shop g, String vehicleId, @Nullable String mechanicId) throws Exception {
        String mechanic = mechanicId == null ? "" : ",\"assignedMechanicId\":\"" + mechanicId + "\"";
        return idOf(call(post("/api/v1/work-orders").content("""
                {"vehicleId":"%s","complaint":"Barulho"%s}""".formatted(vehicleId, mechanic)), g.owner())
                .andExpect(status().isCreated()));
    }

    /// A work order in DIAGNOSTICS with one labour line (1 × 40,00 € at 23 %) and, when `typedPart`, one part typed
    /// by hand (2 × 12,50 € at 23 %, 10 % off).
    protected String quotableOrder(Shop g, boolean typedPart) throws Exception {
        String wo = workOrder(g, vehicle(g, true), null);
        labour(g, wo, "Mudança de óleo", 4000, 23);
        if (typedPart) {
            call(post("/api/v1/work-orders/" + wo + "/lines").content("""
                    {"type":"PART","description":"Pastilhas","quantity":2,"unitPriceCents":1250,"discountPercent":10,
                     "vatRate":23}"""), g.owner()).andExpect(status().isCreated());
        }
        return wo;
    }

    protected ResultActions labour(Shop g, String wo, String description, long cents, int vat) throws Exception {
        return call(post("/api/v1/work-orders/" + wo + "/lines").content("""
                {"type":"LABOR","description":"%s","quantity":1,"unitPriceCents":%d,"vatRate":%d}"""
                .formatted(description, cents, vat)), g.owner()).andExpect(status().isCreated());
    }

    protected ResultActions send(String token, String wo) throws Exception {
        return call(post("/api/v1/work-orders/" + wo + "/quotes"), token);
    }

    /// Sends a quote as the owner; returns the response body.
    protected String sendQuote(Shop g, String wo) throws Exception {
        return send(g.owner(), wo).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    protected static String tokenOfQuote(String sentBody) {
        return JsonPath.read(sentBody, "$.statusToken");
    }

    protected static String idOfQuote(String sentBody) {
        return JsonPath.read(sentBody, "$.quote.id");
    }

    protected static String publicQuote(Shop g, String token) {
        return "/api/v1/public/" + g.slug() + "/quotes/" + token;
    }

    protected ResultActions decide(String token, String wo, String quoteId, String body) throws Exception {
        return call(post("/api/v1/work-orders/" + wo + "/quotes/" + quoteId + "/decision").content(body), token);
    }

    protected ResultActions approveOnLink(Shop g, String token) throws Exception {
        return publicCall(post(publicQuote(g, token) + "/approve").content("{\"name\":\"Ana Silva\",\"accepted\":true}"));
    }

    protected String workOrderStatus(Shop g, String wo) throws Exception {
        return JsonPath.read(call(get("/api/v1/work-orders/" + wo), g.owner()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.status");
    }

    /// Moves a SENT quote's validity into the past. As the table owner (`asp_owner`, still under row-level security
    /// for the quote's workshop): the app role may not change `sent_at` or `valid_until` (V18 column grant).
    protected void pastValidity(Shop g, String quoteId) {
        int updated = asOwner(g, connection -> {
            try (PreparedStatement update = connection.prepareStatement("""
                    update quote set sent_at = now() - interval '20 days', valid_until = now() - interval '1 hour'
                    where id = ?::uuid""")) {
                update.setString(1, quoteId);
                return update.executeUpdate();
            }
        });
        if (updated != 1) {
            throw new IllegalStateException("quote not found");
        }
    }

    /// The status column as stored (not the status shown, which reads EXPIRED at once).
    protected String storedStatus(Shop g, String quoteId) {
        return asWorkshop(g, connection -> {
            try (PreparedStatement select = connection.prepareStatement("select status from quote where id = ?::uuid")) {
                select.setString(1, quoteId);
                try (ResultSet rs = select.executeQuery()) {
                    return rs.next() ? rs.getString(1) : "missing";
                }
            }
        });
    }

    protected interface Sql<T> {
        T run(Connection connection) throws java.sql.SQLException;
    }

    protected <T> T asWorkshop(Shop g, Sql<T> sql) {
        return jdbc.execute((Connection connection) -> {
            try (PreparedStatement bind = connection.prepareStatement("select set_config('app.tenant_id', ?, false)")) {
                bind.setString(1, g.id());
                bind.execute();
            }
            try {
                return sql.run(connection);
            } finally {
                try (PreparedStatement unbind = connection.prepareStatement(
                        "select set_config('app.tenant_id', '', false)")) {
                    unbind.execute();
                }
            }
        });
    }

    /// Runs SQL on a connection of its own as `asp_owner` (the migrations' role, not the app's) for the workshop.
    protected <T> T asOwner(Shop g, Sql<T> sql) {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, OWNER_ROLE, OWNER_ROLE);
                PreparedStatement bind = connection.prepareStatement("select set_config('app.tenant_id', ?, false)")) {
            bind.setString(1, g.id());
            bind.execute();
            return sql.run(connection);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    protected List<String> quoteStatuses(Shop g, String wo) throws Exception {
        return JsonPath.read(call(get("/api/v1/work-orders/" + wo + "/quotes"), g.owner()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$[*].status");
    }
}

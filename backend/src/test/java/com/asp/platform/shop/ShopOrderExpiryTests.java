package com.asp.platform.shop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.asp.platform.shop.application.ShopOrderExpiry;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;

/// The hourly expiry over every open workshop, run directly (scheduling is off in the tests): open orders past
/// their hold expire and give their stock back, in each workshop; orders still held, collected or cancelled stay.
class ShopOrderExpiryTests extends ShopTestSupport {

    @Autowired
    ShopOrderExpiry expiry;

    @Autowired
    DataSource dataSource;

    @Test
    void should_expire_open_orders_past_their_hold_in_every_workshop_and_release_their_stock() throws Exception {
        Shop a = shop("Loja Expira A", true);
        Shop b = shop("Loja Expira B", true);
        String filterA = product(a, "FLT-OL-01", "Filtro de óleo", 1200, true, 5);
        String filterB = product(b, "FLT-OL-01", "Filtro de óleo", 1200, true, 5);
        String placedToken = placeAndGetToken(a, filterA, 2);
        placeAndGetToken(a, filterA, 1);
        call(post("/api/v1/shop-orders/" + orderId(a, 2) + "/ready").content("{\"version\":0}"), a.owner())
                .andExpect(status().isOk());
        placeAndGetToken(b, filterB, 3);
        placeAndGetToken(b, filterB, 1);
        call(post("/api/v1/shop-orders/" + orderId(b, 2) + "/collect")
                .content("{\"paymentMethod\":\"CASH\",\"version\":0}"), b.owner()).andExpect(status().isOk());

        // Nothing is due yet.
        expiry.expireDue(Instant.now());
        call(get("/api/v1/shop-orders").param("status", "EXPIRED"), a.owner()).andExpect(jsonPath("$.length()").value(0));

        expiry.expireDue(Instant.now().plus(Duration.ofDays(4)));

        call(get("/api/v1/shop-orders/" + orderId(a, 1)), a.owner())
                .andExpect(jsonPath("$.status").value("EXPIRED"))
                .andExpect(jsonPath("$.cancelledBy").value("EXPIRY"));
        call(get("/api/v1/shop-orders/" + orderId(a, 2)), a.owner()).andExpect(jsonPath("$.status").value("EXPIRED"));
        call(get("/api/v1/shop-orders/" + orderId(b, 1)), b.owner()).andExpect(jsonPath("$.status").value("EXPIRED"));
        call(get("/api/v1/shop-orders/" + orderId(b, 2)), b.owner()).andExpect(jsonPath("$.status").value("COLLECTED"));
        assertThat(available(a, filterA)).isEqualTo(5);
        assertThat(onHand(a, filterA)).isEqualTo(5);
        assertThat(available(b, filterB)).isEqualTo(4);
        publicCall(get(a.publicPath("/orders/" + placedToken)))
                .andExpect(jsonPath("$.status").value("EXPIRED"))
                .andExpect(jsonPath("$.canCancel").value(false));

        // Run again: nothing left to expire, nothing changes.
        expiry.expireDue(Instant.now().plus(Duration.ofDays(4)));
        call(get("/api/v1/shop-orders/" + orderId(a, 1)), a.owner()).andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void should_lock_jobs_in_the_shedlock_table_as_the_app_user() {
        JdbcTemplateLockProvider locks = new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build());
        LockConfiguration config = new LockConfiguration(Instant.now(), "shop-order-expiry-test",
                Duration.ofMinutes(5), Duration.ZERO);

        Optional<SimpleLock> first = locks.lock(config);
        assertThat(first).isPresent();
        assertThat(locks.lock(config)).as("a second instance does not get the lock").isEmpty();
        first.orElseThrow().unlock();
        Optional<SimpleLock> again = locks.lock(config);
        assertThat(again).isPresent();
        again.orElseThrow().unlock();
    }
}

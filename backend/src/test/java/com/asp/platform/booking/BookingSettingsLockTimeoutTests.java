package com.asp.platform.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;

import com.asp.platform.shared.TenantScope;

/// A booking takes the workshop's settings lock, and creates the settings row on first use. When another
/// transaction holds that lock, or has created the row and not committed yet, the request waits about 3 s and then
/// answers 409 `try-again`, instead of hanging.
class BookingSettingsLockTimeoutTests extends BookingTestSupport {

    private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(3);

    @Autowired
    private DataSource dataSource;

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void should_answer_try_again_when_a_first_use_of_the_settings_stays_uncommitted() throws Exception {
        Workshop w = workshop("Oficina Primeiro Uso Preso", true);
        String oil = service(w.owner(), "Mudança de óleo", 4000, true, true);
        String slot = slotInDefaultHours();

        try (Connection other = connectionOf(w)) {
            other.setAutoCommit(false);
            try (PreparedStatement insert = other.prepareStatement("""
                    insert into booking_settings (id, slot_minutes, cars_per_slot, lead_time_hours, horizon_days,
                                                  cancel_until_hours, version, created_at, updated_at)
                    values (?, 30, 1, 24, 30, 24, 0, now(), now())""")) {
                insert.setObject(1, UUID.randomUUID());
                assertThat(insert.executeUpdate()).isEqualTo(1);
            }

            expectTryAgainAfterTheTimeout(w, slot, oil);

            other.rollback();
        }
        // Once it is gone, the first use goes through.
        book(w, slot, oil).andExpect(status().isCreated());
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void should_answer_try_again_when_the_settings_row_stays_locked() throws Exception {
        Workshop w = workshop("Oficina Definicoes Presas", true);
        String oil = service(w.owner(), "Mudança de óleo", 4000, true, true);
        openEveryDay(w, 1);
        String slot = freeSlots(w, tomorrow()).getFirst();

        try (Connection other = connectionOf(w)) {
            other.setAutoCommit(false);
            try (PreparedStatement lock = other.prepareStatement("select id from booking_settings for update");
                    ResultSet rows = lock.executeQuery()) {
                assertThat(rows.next()).isTrue();
            }

            expectTryAgainAfterTheTimeout(w, slot, oil);

            other.rollback();
        }
        book(w, slot, oil).andExpect(status().isCreated());
    }

    private void expectTryAgainAfterTheTimeout(Workshop w, String slot, String oil) throws Exception {
        long started = System.nanoTime();
        book(w, slot, oil)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("try-again"));
        Duration waited = Duration.ofNanos(System.nanoTime() - started);
        assertThat(waited).isGreaterThanOrEqualTo(LOCK_TIMEOUT.minusMillis(500)).isLessThan(Duration.ofSeconds(15));
        call(get("/api/v1/bookings"), w.owner()).andExpect(jsonPath("$.length()").value(0));
    }

    /// A connection of the app's pool bound to the workshop (row-level security), as a parallel request's would be.
    private Connection connectionOf(Workshop w) throws Exception {
        return TenantScope.callAs(UUID.fromString(w.id()), dataSource::getConnection);
    }

    /// A slot of the default hours (Monday–Friday 09:00, at least 24 h ahead) in Lisbon.
    private static String slotInDefaultHours() {
        java.time.LocalDate day = java.time.LocalDate.now(LISBON).plusDays(2);
        while (day.getDayOfWeek().getValue() > 5) {
            day = day.plusDays(1);
        }
        return day.atTime(9, 0).atZone(LISBON).toInstant().toString();
    }
}

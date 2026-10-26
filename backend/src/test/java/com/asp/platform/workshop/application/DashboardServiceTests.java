package com.asp.platform.workshop.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.asp.platform.identity.PublicWorkshop;
import com.asp.platform.identity.TeamMember;
import com.asp.platform.identity.WorkshopDirectory;
import com.asp.platform.workshop.domain.WorkOrderStatus;

class DashboardServiceTests {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID RUI = UUID.randomUUID();
    private static final UUID ZE = UUID.randomUUID();

    private final FakeQueries queries = new FakeQueries();

    @Test
    void should_count_the_month_in_the_workshop_time_zone() {
        // 31 Oct 23:30 UTC is still October in Lisbon (UTC+0) but already 1 Nov 00:30 in Madrid (UTC+1).
        Instant now = Instant.parse("2026-10-31T23:30:00Z");

        Dashboard dashboard = service("Europe/Madrid", now).dashboard(TENANT);

        assertThat(dashboard.month()).hasToString("2026-11");
        assertThat(queries.from).isEqualTo(Instant.parse("2026-10-31T23:00:00Z"));
        assertThat(queries.to).isEqualTo(Instant.parse("2026-11-30T23:00:00Z"));
    }

    @Test
    void should_include_every_column_and_every_active_mechanic_with_zeros() {
        UUID other = UUID.randomUUID();
        queries.open.add(new DashboardQueries.OpenOrderCount(WorkOrderStatus.IN_PROGRESS, RUI, 2));
        queries.open.add(new DashboardQueries.OpenOrderCount(WorkOrderStatus.READY, RUI, 1));
        queries.open.add(new DashboardQueries.OpenOrderCount(WorkOrderStatus.READY, null, 3));
        queries.open.add(new DashboardQueries.OpenOrderCount(WorkOrderStatus.DIAGNOSTICS, other, 1));
        queries.delivered = new DashboardQueries.DeliveredTotals(2, 8250, 1898);

        Dashboard dashboard = service("Europe/Lisbon", Instant.parse("2026-10-20T09:00:00Z")).dashboard(TENANT);

        assertThat(dashboard.openByStatus()).containsExactly(
                entry(WorkOrderStatus.DIAGNOSTICS, 1), entry(WorkOrderStatus.AWAITING_APPROVAL, 0),
                entry(WorkOrderStatus.AWAITING_PARTS, 0), entry(WorkOrderStatus.IN_PROGRESS, 2),
                entry(WorkOrderStatus.READY, 4));
        assertThat(dashboard.carsInShop()).isEqualTo(7);
        assertThat(dashboard.deliveredThisMonth()).isEqualTo(2);
        assertThat(dashboard.revenueThisMonth().grossCents()).isEqualTo(10148);
        // The order of a deactivated mechanic counts on the board but not in anyone's workload.
        assertThat(dashboard.mechanicWorkload()).containsExactly(
                new Dashboard.MechanicLoad(RUI, "Rui", 3), new Dashboard.MechanicLoad(ZE, "Zé", 0));
    }

    private DashboardService service(String zone, Instant now) {
        WorkshopDirectory directory = new WorkshopDirectory() {
            @Override
            public ZoneId timezone(UUID tenantId) {
                return ZoneId.of(zone);
            }

            @Override
            public String country(UUID tenantId) {
                return "PT";
            }

            @Override
            public java.util.Optional<com.asp.platform.identity.WorkshopProfile> profile(UUID tenantId) {
                return java.util.Optional.empty();
            }

            @Override
            public long labourRateCents(UUID tenantId) {
                return 4500;
            }

            @Override
            public List<TeamMember> activeMechanics(UUID tenantId) {
                return List.of(new TeamMember(RUI, "Rui"), new TeamMember(ZE, "Zé"));
            }

            @Override
            public Map<UUID, String> memberNames(UUID tenantId) {
                return Map.of(RUI, "Rui", ZE, "Zé");
            }

            @Override
            public boolean isActiveMechanic(UUID tenantId, UUID userId) {
                return userId.equals(RUI) || userId.equals(ZE);
            }

            @Override
            public Optional<PublicWorkshop> publicWorkshop(String slug) {
                return Optional.empty();
            }

            @Override
            public List<UUID> openWorkshops() {
                return List.of();
            }
        };
        return new DashboardService(queries, directory, Clock.fixed(now, ZoneOffset.UTC));
    }

    private static Map.Entry<WorkOrderStatus, Long> entry(WorkOrderStatus status, long count) {
        return Map.entry(status, count);
    }

    private static final class FakeQueries implements DashboardQueries {

        final List<OpenOrderCount> open = new ArrayList<>();
        DeliveredTotals delivered = new DeliveredTotals(0, 0, 0);
        Instant from;
        Instant to;

        @Override
        public List<OpenOrderCount> openOrders() {
            return open;
        }

        @Override
        public DeliveredTotals delivered(Instant from, Instant to) {
            this.from = from;
            this.to = to;
            return delivered;
        }
    }
}

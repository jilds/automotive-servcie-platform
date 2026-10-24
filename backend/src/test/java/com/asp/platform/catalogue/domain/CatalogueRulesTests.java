package com.asp.platform.catalogue.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class CatalogueRulesTests {

    private static final BigDecimal VAT = new BigDecimal("23");

    @Test
    void should_spread_a_package_price_in_proportion_and_add_up_exactly() {
        // Oil change 40.00 + filter 20.00 + inspection 15.00 = 75.00, sold for 60.00.
        List<Long> shares = PackagePricing.allocate(6000, List.of(4000L, 2000L, 1500L));
        assertThat(shares).containsExactly(3200L, 1600L, 1200L);
        assertThat(shares.stream().mapToLong(Long::longValue).sum()).isEqualTo(6000);

        // Thirds that do not divide evenly: the remainder goes to the last line.
        List<Long> thirds = PackagePricing.allocate(1000, List.of(1L, 1L, 1L));
        assertThat(thirds).containsExactly(333L, 333L, 334L);

        // Free services: the price is split evenly.
        assertThat(PackagePricing.allocate(900, List.of(0L, 0L, 0L))).containsExactly(300L, 300L, 300L);
        assertThat(PackagePricing.allocate(500, List.of(700L))).containsExactly(500L);
    }

    @Test
    void should_trim_texts_and_check_the_service_values() {
        ServiceDetails details = new ServiceDetails("  Oil change ", "  ", " Maintenance ", new BigDecimal("0.75"),
                ServicePricing.FIXED, 4000, VAT, 60, true, false);
        assertThat(details.name()).isEqualTo("Oil change");
        assertThat(details.description()).isNull();
        assertThat(details.category()).isEqualTo("Maintenance");

        assertThatThrownBy(() -> fixed(" ", 0, 60)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> fixed("X", -1, 60)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> fixed("X", 0, 4)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_price_an_hourly_service_at_the_labour_rate_rounded_half_up() {
        ServiceDetails diagnostics = new ServiceDetails("Diagnostics", null, null, new BigDecimal("1.25"),
                ServicePricing.HOURLY, 0, VAT, 60, true, false);
        // 1.25 h x 40.10 = 50.125, rounded half up to 50.13.
        assertThat(diagnostics.effectivePriceCents(4010)).isEqualTo(5013);
        assertThat(diagnostics.effectivePriceCents(4500)).isEqualTo(5625);
        assertThat(diagnostics.effectivePriceCents(0)).isZero();
        assertThat(diagnostics.hourly()).isTrue();

        // The stored fixed price is ignored while the pricing is hourly.
        ServiceDetails check = new ServiceDetails("Check", null, null, new BigDecimal("0.75"), ServicePricing.HOURLY,
                9999, VAT, 30, true, false);
        assertThat(check.effectivePriceCents(4500)).isEqualTo(3375);

        ServiceDetails oil = fixed("Oil change", 4000, 60);
        assertThat(oil.effectivePriceCents(4500)).isEqualTo(4000);
        assertThat(oil.hourly()).isFalse();
    }

    private static ServiceDetails fixed(String name, long priceCents, int minutes) {
        return new ServiceDetails(name, null, null, BigDecimal.ONE, ServicePricing.FIXED, priceCents, VAT, minutes,
                true, false);
    }

    @Test
    void should_merge_a_service_listed_twice_and_refuse_an_empty_package() {
        UUID oil = UUID.randomUUID();
        UUID filter = UUID.randomUUID();
        PackageDetails details = new PackageDetails("Annual service", null, 6000, VAT, true, true,
                List.of(new PackageItem(oil, 1), new PackageItem(filter, 1), new PackageItem(oil, 2)));
        assertThat(details.items()).containsExactly(new PackageItem(oil, 3), new PackageItem(filter, 1));

        assertThatThrownBy(() -> new PackageDetails("Empty", null, 0, VAT, true, false, List.of()))
                .isInstanceOfSatisfying(PackageEmptyException.class, e -> {
                    assertThat(e.code()).isEqualTo("package-empty");
                    assertThat(e.field()).isEqualTo("items");
                });
        assertThatThrownBy(() -> new PackageItem(oil, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_refuse_an_inactive_service_or_package() {
        ServiceItem inactive = new ServiceItem(UUID.randomUUID(),
                new ServiceDetails("Old", null, null, BigDecimal.ONE, ServicePricing.FIXED, 0, VAT, 60, false,
                        false));
        assertThatThrownBy(inactive::requireActive).isInstanceOf(ServiceInactiveException.class);

        ServicePackage old = new ServicePackage(UUID.randomUUID(), new PackageDetails("Old", null, 0, VAT, false,
                false, List.of(new PackageItem(UUID.randomUUID(), 1))));
        assertThatThrownBy(old::requireActive).isInstanceOf(PackageInactiveException.class);
    }
}

package com.asp.platform.catalogue.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.asp.platform.catalogue.domain.PackagePricing.Portion;

/// Packages with stock products: items are a service or a product, and a product's share comes out in whole cents
/// per unit where possible, so the lines still add up to exactly the package price.
class PackagePartsRulesTests {

    @Test
    void should_hold_either_a_service_or_a_product() {
        UUID id = UUID.randomUUID();
        assertThat(PackageItem.service(id, 1).kind()).isEqualTo(PackageItemKind.SERVICE);
        assertThat(PackageItem.product(id, 2).kind()).isEqualTo(PackageItemKind.PRODUCT);
        assertThat(PackageItem.product(id, 2).itemId()).isEqualTo(id);
        assertThatThrownBy(() -> new PackageItem(null, null, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PackageItem(id, UUID.randomUUID(), 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_merge_the_same_product_but_not_a_service_with_the_same_id() {
        UUID id = UUID.randomUUID();
        PackageDetails details = new PackageDetails("Revisão", null, 1000, BigDecimal.valueOf(23), true, false,
                List.of(PackageItem.product(id, 1), PackageItem.service(id, 1), PackageItem.product(id, 3)));
        assertThat(details.items()).containsExactly(PackageItem.product(id, 4), PackageItem.service(id, 1));
    }

    @Test
    void should_give_products_shares_in_whole_cents_per_unit_and_the_rest_to_the_last_service() {
        // 80.00 over 40.00 (service), 9.00 (one filter), 48.00 (four litres): the oil gets 4 x 9.90, the filter the
        // remainder.
        List<Long> shares = PackagePricing.allocate(8000, List.of(4000L, 900L, 4800L), List.of(1, 1, 4));
        assertThat(shares).containsExactly(3299L, 741L, 3960L);
        assertThat(shares.stream().mapToLong(Long::longValue).sum()).isEqualTo(8000);
    }

    @Test
    void should_split_evenly_like_before_when_every_item_is_a_single_unit() {
        assertThat(PackagePricing.allocate(6000, List.of(4000L, 2000L, 1500L), List.of(1, 1, 1)))
                .isEqualTo(PackagePricing.allocate(6000, List.of(4000L, 2000L, 1500L)))
                .containsExactly(3200L, 1600L, 1200L);
    }

    @Test
    void should_put_the_rest_on_the_last_share_when_every_item_has_several_units() {
        List<Long> shares = PackagePricing.allocate(1001, List.of(600L, 600L), List.of(2, 3));
        assertThat(shares).containsExactly(500L, 501L);
        assertThat(PackagePricing.split(501, 3)).containsExactly(new Portion(3, 167));
        assertThat(PackagePricing.split(500, 3)).containsExactly(new Portion(1, 166), new Portion(2, 167));
    }

    @Test
    void should_split_a_share_into_one_or_two_portions_that_add_up_exactly() {
        assertThat(PackagePricing.split(3960, 4)).containsExactly(new Portion(4, 990));
        List<Portion> portions = PackagePricing.split(1001, 4);
        assertThat(portions).containsExactly(new Portion(3, 250), new Portion(1, 251));
        assertThat(portions.stream().mapToLong(p -> p.quantity() * p.unitCents()).sum()).isEqualTo(1001);
        assertThat(PackagePricing.split(0, 2)).containsExactly(new Portion(2, 0));
    }
}

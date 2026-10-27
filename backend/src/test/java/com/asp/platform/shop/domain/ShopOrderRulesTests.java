package com.asp.platform.shop.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/// Money, availability and status moves of a shop order, without Spring.
class ShopOrderRulesTests {

    private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");

    private static ShopOrderLine line(int quantity, long cents, String vat) {
        return new ShopOrderLine(UUID.randomUUID(), UUID.randomUUID(), "SKU", "Filtro", "un", quantity, cents,
                new BigDecimal(vat));
    }

    private static ShopOrder placed(ShopOrderLine... lines) {
        return ShopOrder.place(UUID.randomUUID(), 1, new ShopContact("Ana", "912345678", null, null), null, false,
                "0".repeat(64), List.of(lines), NOW, 3);
    }

    @Test
    void should_round_the_price_with_vat_half_up_per_unit_then_multiply() {
        assertThat(line(2, 1200, "23").unitPriceWithVatCents()).isEqualTo(1476);
        assertThat(line(2, 1200, "23").lineTotalCents()).isEqualTo(2952);
        // 250 × 1.23 = 307.5 → 308; 50 × 1.06 = 53; 1 × 1.13 = 1.13 → 1.
        assertThat(line(3, 250, "23").unitPriceWithVatCents()).isEqualTo(308);
        assertThat(line(3, 250, "23").lineTotalCents()).isEqualTo(924);
        assertThat(line(1, 50, "6").unitPriceWithVatCents()).isEqualTo(53);
        assertThat(line(1, 1, "13").unitPriceWithVatCents()).isEqualTo(1);
        assertThat(line(1, 1000, "0").unitPriceWithVatCents()).isEqualTo(1000);
        assertThat(placed(line(2, 1200, "23"), line(3, 250, "23")).totalCents()).isEqualTo(2952 + 924);
    }

    @Test
    void should_describe_availability_without_numbers() {
        assertThat(Availability.of(BigDecimal.TEN, BigDecimal.ZERO)).isEqualTo(Availability.IN_STOCK);
        assertThat(Availability.of(new BigDecimal("4"), BigDecimal.ZERO)).isEqualTo(Availability.IN_STOCK);
        assertThat(Availability.of(new BigDecimal("3"), BigDecimal.ZERO)).isEqualTo(Availability.LAST_UNITS);
        assertThat(Availability.of(new BigDecimal("5"), new BigDecimal("5"))).isEqualTo(Availability.LAST_UNITS);
        assertThat(Availability.of(new BigDecimal("6"), new BigDecimal("5"))).isEqualTo(Availability.IN_STOCK);
        assertThat(Availability.of(new BigDecimal("0.5"), BigDecimal.ZERO)).isEqualTo(Availability.LAST_UNITS);
        assertThat(Availability.of(BigDecimal.ZERO, BigDecimal.ZERO)).isEqualTo(Availability.OUT);
        assertThat(Availability.of(new BigDecimal("-1"), BigDecimal.ONE)).isEqualTo(Availability.OUT);
    }

    @Test
    void should_hold_from_placement_and_again_from_ready() {
        ShopOrder order = placed(line(1, 1000, "23"));
        assertThat(order.holdUntil()).isEqualTo(NOW.plus(Duration.ofDays(3)));
        assertThat(order.canCancel()).isTrue();

        Instant later = NOW.plus(Duration.ofDays(2));
        order.markReady(later, 5);
        assertThat(order.status()).isEqualTo(ShopOrderStatus.READY);
        assertThat(order.holdUntil()).isEqualTo(later.plus(Duration.ofDays(5)));
        assertThat(order.canCancel()).isFalse();
        assertThatThrownBy(() -> order.cancelByCustomer(later)).isInstanceOf(ShopOrderNotOpenException.class);
        assertThat(order.isDue(later.plus(Duration.ofDays(5)))).isFalse();
        assertThat(order.isDue(later.plus(Duration.ofDays(5)).plusSeconds(1))).isTrue();
    }

    @Test
    void should_allow_only_the_moves_of_the_contract() {
        ShopOrder collected = placed(line(1, 1000, "23"));
        collected.collect(PaymentMethod.CASH, UUID.randomUUID(), NOW);
        assertThat(collected.status()).isEqualTo(ShopOrderStatus.COLLECTED);
        assertThatThrownBy(() -> collected.markReady(NOW, 3)).isInstanceOf(ShopOrderNotOpenException.class);
        assertThatThrownBy(() -> collected.cancelByStaff("x", NOW)).isInstanceOf(ShopOrderNotOpenException.class);
        assertThatThrownBy(() -> collected.expire(NOW.plus(Duration.ofDays(30))))
                .isInstanceOf(ShopOrderNotOpenException.class);

        ShopOrder cancelled = placed(line(1, 1000, "23"));
        assertThatThrownBy(() -> cancelled.cancelByStaff(" ", NOW)).isInstanceOf(CancelReasonRequiredException.class);
        cancelled.cancelByStaff(" Sem stock ", NOW);
        assertThat(cancelled.cancelReason()).isEqualTo("Sem stock");
        assertThat(cancelled.cancelledBy()).isEqualTo(CancelledBy.STAFF);
        assertThatThrownBy(() -> cancelled.collect(PaymentMethod.CARD, UUID.randomUUID(), NOW))
                .isInstanceOf(ShopOrderNotOpenException.class);

        ShopOrder held = placed(line(1, 1000, "23"));
        assertThatThrownBy(() -> held.expire(NOW.plus(Duration.ofDays(1)))).isInstanceOf(ShopOrderNotOpenException.class);
        held.expire(NOW.plus(Duration.ofDays(4)));
        assertThat(held.status()).isEqualTo(ShopOrderStatus.EXPIRED);
        assertThat(held.cancelledBy()).isEqualTo(CancelledBy.EXPIRY);
    }

    @Test
    void should_refuse_orders_without_lines_or_with_a_product_twice() {
        ShopOrderLine one = line(1, 1000, "23");
        ShopOrderLine sameProduct = new ShopOrderLine(UUID.randomUUID(), one.productId(), "SKU", "Filtro", "un", 2,
                1000, BigDecimal.TEN);
        assertThatThrownBy(ShopOrderRulesTests::placed).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> placed(one, sameProduct)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> line(21, 1000, "23")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> line(0, 1000, "23")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PaymentMethod.parse("BITCOIN")).isInstanceOf(InvalidPaymentMethodException.class);
        assertThatThrownBy(() -> PaymentMethod.parse(null)).isInstanceOf(InvalidPaymentMethodException.class);
        assertThat(PaymentMethod.parse("MULTIBANCO")).isEqualTo(PaymentMethod.MULTIBANCO);
    }
}

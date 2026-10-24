package com.asp.platform.stock.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.asp.platform.shared.StaleDataException;
import com.asp.platform.stock.ReservationSource;

/// The stock rules: the ledger arithmetic of the quantity on hand, what is available, reservations and purchase
/// orders.
class StockRulesTests {

    private static final UUID USER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-23T10:00:00Z");

    @Nested
    class Ledger {

        @Test
        void should_add_receipts_and_take_away_issues_and_adjustments() {
            Product product = product(true);

            StockMovement receipt = product.record(UUID.randomUUID(), MovementType.RECEIPT, qty("10"), null, null,
                    USER, NOW);
            product.record(UUID.randomUUID(), MovementType.ISSUE, qty("-3"), null, null, USER, NOW);
            product.record(UUID.randomUUID(), MovementType.ADJUSTMENT, qty("-1.5"), null, "Partido", USER, NOW);
            product.record(UUID.randomUUID(), MovementType.ADJUSTMENT, qty("0.25"), null, "Contagem", USER, NOW);

            assertThat(product.onHand()).isEqualByComparingTo("5.75");
            assertThat(receipt.productId()).isEqualTo(product.id());
            assertThat(receipt.quantity()).isEqualByComparingTo("10");
        }

        @Test
        void should_never_leave_less_than_nothing_on_hand() {
            Product product = product(true);
            product.record(UUID.randomUUID(), MovementType.RECEIPT, qty("2"), null, null, USER, NOW);

            assertThatThrownBy(() -> product.record(UUID.randomUUID(), MovementType.ISSUE, qty("-2.001"), null, null,
                    USER, NOW))
                    .isInstanceOf(InsufficientStockException.class)
                    .hasFieldOrPropertyWithValue("code", "insufficient-stock");
            assertThatThrownBy(() -> product.record(UUID.randomUUID(), MovementType.ADJUSTMENT, qty("-3"), null,
                    "Perdido", USER, NOW))
                    .isInstanceOf(InsufficientStockException.class);
            assertThat(product.onHand()).as("a refused movement changes nothing").isEqualByComparingTo("2");

            product.record(UUID.randomUUID(), MovementType.ISSUE, qty("-2"), null, null, USER, NOW);
            assertThat(product.onHand()).isEqualByComparingTo("0");
        }

        @Test
        void should_require_a_reason_for_adjustments_and_the_right_sign_for_each_type() {
            Product product = product(true);

            assertThatThrownBy(() -> product.record(UUID.randomUUID(), MovementType.ADJUSTMENT, qty("1"), null, "  ",
                    USER, NOW))
                    .isInstanceOf(AdjustmentReasonRequiredException.class)
                    .hasFieldOrPropertyWithValue("code", "adjustment-reason-required")
                    .hasFieldOrPropertyWithValue("field", "reason");
            assertThatThrownBy(() -> product.record(UUID.randomUUID(), MovementType.RECEIPT, qty("-1"), null, null,
                    USER, NOW)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> product.record(UUID.randomUUID(), MovementType.ISSUE, qty("1"), null, null, USER,
                    NOW)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> product.record(UUID.randomUUID(), MovementType.ADJUSTMENT, qty("0"), null,
                    "Nada", USER, NOW)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> product.record(UUID.randomUUID(), MovementType.RECEIPT, qty("0.0001"), null,
                    null, USER, NOW)).as("three decimals at most").isInstanceOf(IllegalArgumentException.class);
            assertThat(product.onHand()).isEqualByComparingTo("0");
        }

        @Test
        void should_keep_the_version_for_edits_only() {
            Product product = new Product(UUID.randomUUID(), details(true), qty("1"), 4);

            assertThatThrownBy(() -> product.requireVersion(3)).isInstanceOf(StaleDataException.class);
            product.requireVersion(4);
        }
    }

    @Nested
    class Availability {

        @Test
        void should_subtract_active_reservations_from_what_is_on_hand() {
            StockLevel level = new StockLevel(qty("5"), qty("3.5"));

            assertThat(level.available()).isEqualByComparingTo("1.5");
            assertThat(level.isLow(qty("2"))).isTrue();
            assertThat(level.isLow(qty("1.5"))).isFalse();
            assertThat(level.isLow(BigDecimal.ZERO)).as("no minimum, never low").isFalse();
            assertThat(new StockLevel(qty("0"), qty("0")).isLow(BigDecimal.ZERO)).isFalse();
        }

        @Test
        void should_refuse_more_than_is_available() {
            StockLevel level = new StockLevel(qty("5"), qty("4"));

            level.requireAvailable(qty("1"));
            assertThatThrownBy(() -> level.requireAvailable(qty("1.001")))
                    .isInstanceOf(InsufficientStockException.class);
        }
    }

    @Nested
    class Reservations {

        @Test
        void should_reserve_what_is_available_and_end_once() {
            Product product = new Product(UUID.randomUUID(), details(true), qty("3"), 0);
            UUID order = UUID.randomUUID();
            UUID line = UUID.randomUUID();

            Reservation reservation = Reservation.reserve(UUID.randomUUID(), product, new StockLevel(qty("3"),
                    qty("1")), qty("2"), ReservationSource.WORK_ORDER, order, line);

            assertThat(reservation.status()).isEqualTo(ReservationStatus.ACTIVE);
            assertThat(reservation.sourceId()).isEqualTo(order);
            assertThat(reservation.sourceLineId()).isEqualTo(line);
            reservation.consume();
            assertThat(reservation.status()).isEqualTo(ReservationStatus.CONSUMED);
            assertThatThrownBy(reservation::release).isInstanceOf(IllegalStateException.class);

            Reservation other = Reservation.reserve(UUID.randomUUID(), product, new StockLevel(qty("3"), qty("0")),
                    qty("3"), ReservationSource.WORK_ORDER, order, UUID.randomUUID());
            other.release();
            assertThat(other.status()).isEqualTo(ReservationStatus.RELEASED);
            assertThatThrownBy(other::consume).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void should_refuse_more_than_is_available_or_an_inactive_product() {
            Product product = new Product(UUID.randomUUID(), details(true), qty("3"), 0);
            assertThatThrownBy(() -> Reservation.reserve(UUID.randomUUID(), product,
                    new StockLevel(qty("3"), qty("2")), qty("2"), ReservationSource.WORK_ORDER, UUID.randomUUID(),
                    UUID.randomUUID()))
                    .isInstanceOf(InsufficientStockException.class);

            Product inactive = new Product(UUID.randomUUID(), details(false), qty("3"), 0);
            assertThatThrownBy(() -> Reservation.reserve(UUID.randomUUID(), inactive,
                    new StockLevel(qty("3"), qty("0")), qty("1"), ReservationSource.WORK_ORDER, UUID.randomUUID(),
                    UUID.randomUUID()))
                    .isInstanceOf(ProductInactiveException.class)
                    .hasFieldOrPropertyWithValue("code", "product-inactive");
        }
    }

    @Nested
    class PurchaseOrders {

        @Test
        void should_go_from_draft_to_ordered_to_received_and_change_only_as_a_draft() {
            PurchaseOrder order = PurchaseOrder.draft(UUID.randomUUID(), 1, poDetails("Auto Peças Lda"));
            order.update(poDetails("Auto Peças Norte"));
            assertThat(order.details().supplier()).isEqualTo("Auto Peças Norte");

            assertThatThrownBy(() -> order.markReceived(NOW))
                    .isInstanceOf(IllegalPurchaseOrderStatusException.class)
                    .hasFieldOrPropertyWithValue("code", "illegal-purchase-order-status");
            order.markOrdered(NOW);
            assertThat(order.status()).isEqualTo(PurchaseOrderStatus.ORDERED);
            assertThat(order.orderedAt()).isEqualTo(NOW);
            assertThatThrownBy(() -> order.update(poDetails("Outro")))
                    .isInstanceOf(PurchaseOrderLockedException.class)
                    .hasFieldOrPropertyWithValue("code", "purchase-order-locked");

            order.markReceived(NOW);
            assertThat(order.status()).isEqualTo(PurchaseOrderStatus.RECEIVED);
            assertThatThrownBy(() -> order.markReceived(NOW)).isInstanceOf(IllegalPurchaseOrderStatusException.class);
            assertThatThrownBy(() -> order.markOrdered(NOW)).isInstanceOf(IllegalPurchaseOrderStatusException.class);
        }

        @Test
        void should_total_the_lines_rounding_each_half_up() {
            PurchaseOrder order = PurchaseOrder.draft(UUID.randomUUID(), 1, new PurchaseOrderDetails("Fornecedor", null,
                    List.of(new PurchaseOrderLine(UUID.randomUUID(), UUID.randomUUID(), qty("1.5"), 333),
                            new PurchaseOrderLine(UUID.randomUUID(), UUID.randomUUID(), qty("2"), 1000))));

            assertThat(order.totalCents()).as("499.5 rounds to 500, plus 2000").isEqualTo(2500);
        }

        @Test
        void should_need_a_supplier_and_at_least_one_line() {
            assertThatThrownBy(() -> new PurchaseOrderDetails(" ", null, List.of(line())))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new PurchaseOrderDetails("Fornecedor", null, List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new PurchaseOrderLine(UUID.randomUUID(), UUID.randomUUID(), qty("0"), 100))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void should_trim_product_texts_and_refuse_bad_values() {
        ProductDetails d = new ProductDetails("  OF-712 ", " Filtro de óleo ", " ", "un", 450, 900, qty("23"),
                qty("2"), "", true, false);
        assertThat(d.sku()).isEqualTo("OF-712");
        assertThat(d.name()).isEqualTo("Filtro de óleo");
        assertThat(d.brand()).isNull();
        assertThat(d.bin()).isNull();

        assertThatThrownBy(() -> new ProductDetails("", "x", null, "un", 0, 0, qty("23"), qty("0"), null, true,
                false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProductDetails("A", "x", null, "un", -1, 0, qty("23"), qty("0"), null, true,
                false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProductDetails("A", "x", null, "un", 0, 0, qty("101"), qty("0"), null, true,
                false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProductDetails("A", "x", null, "un", 0, 0, qty("23"), qty("-1"), null, true,
                false)).isInstanceOf(IllegalArgumentException.class);
    }

    private static Product product(boolean active) {
        return new Product(UUID.randomUUID(), details(active));
    }

    private static ProductDetails details(boolean active) {
        return new ProductDetails("OF-712", "Filtro de óleo", "Mann", "un", 450, 900, qty("23"), qty("2"), "A-03",
                active, false);
    }

    private static PurchaseOrderDetails poDetails(String supplier) {
        return new PurchaseOrderDetails(supplier, null, List.of(line()));
    }

    private static PurchaseOrderLine line() {
        return new PurchaseOrderLine(UUID.randomUUID(), UUID.randomUUID(), qty("4"), 450);
    }

    private static BigDecimal qty(String value) {
        return new BigDecimal(value);
    }
}

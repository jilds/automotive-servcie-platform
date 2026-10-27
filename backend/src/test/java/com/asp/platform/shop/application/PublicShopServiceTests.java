package com.asp.platform.shop.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;

import com.asp.platform.shop.domain.ShopBusyException;
import com.asp.platform.shop.domain.ShopOrderRepository;
import com.asp.platform.shop.domain.ShopSettings;
import com.asp.platform.shop.domain.ShopSettingsRepository;
import com.asp.platform.stock.ReservationSource;
import com.asp.platform.stock.StockItem;
import com.asp.platform.stock.StockReservations;

/// Placing an order: products are reserved in the stock module's lock order before the settings lock, and a
/// workshop holding `app.shop.max-open-orders` open orders takes no more.
class PublicShopServiceTests {

    // Signed comparison puts HIGH first (negative most significant long); PostgreSQL and LOCK_ORDER put LOW first.
    private static final UUID LOW = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID HIGH = UUID.fromString("80000000-0000-4000-8000-000000000001");

    private final ShopOrderRepository orders = mock(ShopOrderRepository.class);
    private final ShopSettingsRepository settings = mock(ShopSettingsRepository.class);
    private final StockReservations stock = mock(StockReservations.class);
    private final PublicShopService service = new PublicShopService(orders, settings, stock, new ShopProperties(2),
            mock(ApplicationEventPublisher.class),
            Clock.fixed(Instant.parse("2026-10-27T10:00:00Z"), ZoneOffset.UTC));

    @Test
    void should_lock_products_in_database_order_which_signed_uuid_order_gets_wrong() {
        assertThat(HIGH.compareTo(LOW)).isNegative();
        assertThat(StockReservations.LOCK_ORDER.compare(LOW, HIGH)).isNegative();
    }

    @Test
    void should_reserve_in_lock_order_before_taking_the_settings_lock() {
        offered(HIGH, LOW);
        when(settings.lockOrCreate()).thenReturn(ShopSettings.defaults(UUID.randomUUID()));
        when(orders.countOpen()).thenReturn(1L);
        when(orders.nextNumber()).thenReturn(1);
        when(orders.save(any())).thenAnswer(call -> call.getArgument(0));

        service.place(UUID.randomUUID(), "PT", command(HIGH, LOW));

        InOrder order = inOrder(stock, settings);
        order.verify(stock).reserve(eq(LOW), any(), eq(ReservationSource.SHOP_ORDER), any(), any());
        order.verify(stock).reserve(eq(HIGH), any(), eq(ReservationSource.SHOP_ORDER), any(), any());
        order.verify(settings).lockOrCreate();
    }

    @Test
    void should_refuse_a_new_order_once_the_workshop_holds_the_most_open_orders() {
        offered(LOW);
        when(settings.lockOrCreate()).thenReturn(ShopSettings.defaults(UUID.randomUUID()));
        when(orders.countOpen()).thenReturn(2L);

        assertThatThrownBy(() -> service.place(UUID.randomUUID(), "PT", command(LOW)))
                .isInstanceOf(ShopBusyException.class);
        verify(orders, never()).save(any());
    }

    private void offered(UUID... ids) {
        Map<UUID, StockItem> items = new HashMap<>();
        for (UUID id : ids) {
            items.put(id, new StockItem(id, "SKU-" + id.toString().substring(0, 4), "Filtro", "un", 1200,
                    new BigDecimal("23"), true, true));
        }
        when(stock.items(anyCollection())).thenReturn(items);
    }

    private static PlaceOrderCommand command(UUID... ids) {
        List<PlaceOrderCommand.Line> lines = Arrays.stream(ids).map(id -> new PlaceOrderCommand.Line(id, 1))
                .toList();
        return new PlaceOrderCommand(lines, "Ana Silva", "912345678", null, null, false, "pt-PT");
    }
}

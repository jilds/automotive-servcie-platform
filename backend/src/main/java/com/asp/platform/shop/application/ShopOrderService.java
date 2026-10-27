package com.asp.platform.shop.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.asp.platform.identity.WorkshopDirectory;
import com.asp.platform.shop.OrderStatusChanged;
import com.asp.platform.shop.domain.PaymentMethod;
import com.asp.platform.shop.domain.ShopOrder;
import com.asp.platform.shop.domain.ShopOrderNotFoundException;
import com.asp.platform.shop.domain.ShopOrderRepository;
import com.asp.platform.shop.domain.ShopOrderStatus;
import com.asp.platform.shop.domain.ShopRangeTooLongException;
import com.asp.platform.shop.domain.ShopSettings;
import com.asp.platform.shop.domain.ShopSettingsRepository;
import com.asp.platform.stock.ReservationSource;
import com.asp.platform.stock.StockReservations;

/// The front desk's shop orders: the list, preparing (READY), the hand-over at the counter (COLLECTED, the stock is
/// issued) and cancelling (the stock is released), plus the expiry of orders past their hold time. Row-level
/// security keeps every query inside the current workshop, so another workshop's order reads as not found. Every
/// decision needs the version the front desk read (409 `stale-data`).
@Service
public class ShopOrderService {

    /// Days the list shows when the caller gives no start date: today − 30 to today.
    static final int DEFAULT_DAYS = 30;

    private final ShopOrderRepository orders;
    private final ShopSettingsRepository settings;
    private final StockReservations stock;
    private final WorkshopDirectory directory;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    ShopOrderService(ShopOrderRepository orders, ShopSettingsRepository settings, StockReservations stock,
            WorkshopDirectory directory, ApplicationEventPublisher events, Clock clock) {
        this.orders = orders;
        this.settings = settings;
        this.stock = stock;
        this.directory = directory;
        this.events = events;
        this.clock = clock;
    }

    /// Orders placed on the days `from` to `to` (both included, in the workshop's time zone), newest first.
    /// Defaults: today − 30 days to today. At most 124 days apart (400 `validation` on `to`).
    @Transactional(readOnly = true)
    public List<ShopOrderView> list(UUID tenantId, @Nullable LocalDate from, @Nullable LocalDate to,
            @Nullable ShopOrderStatus status) {
        ZoneId zone = directory.timezone(tenantId);
        LocalDate last = to != null ? to : LocalDate.now(clock.withZone(zone));
        LocalDate first = from != null ? from : last.minusDays(DEFAULT_DAYS);
        if (last.isBefore(first)) {
            return List.of();
        }
        if (ChronoUnit.DAYS.between(first, last) > ShopRangeTooLongException.MAX_DAYS) {
            throw new ShopRangeTooLongException();
        }
        List<ShopOrder> found = orders.findPlacedBetween(first.atStartOfDay(zone).toInstant(),
                last.plusDays(1).atStartOfDay(zone).toInstant(), status);
        return views(tenantId, found);
    }

    @Transactional(readOnly = true)
    public ShopOrderView get(UUID tenantId, UUID id) {
        return view(tenantId, order(id));
    }

    /// Orders waiting to be prepared (PLACED), for the dashboard tile.
    @Transactional(readOnly = true)
    public long pendingCount() {
        return orders.countByStatus(ShopOrderStatus.PLACED);
    }

    /// PLACED → READY; the order is kept for the workshop's hold days from now. Tells the customer.
    @Transactional
    public ShopOrderView ready(UUID tenantId, UUID id, long expectedVersion) {
        ShopOrder order = order(id);
        order.requireVersion(expectedVersion);
        ShopSettings current = currentSettings();
        order.markReady(clock.instant(), current.holdDays());
        ShopOrder saved = orders.save(order);
        announce(tenantId, saved, OrderStatusChanged.Change.READY, null, current.pickupNote());
        return view(tenantId, saved);
    }

    /// PLACED or READY → COLLECTED: the customer paid at the counter with `method`; the reserved stock is issued.
    ///
    /// @throws com.asp.platform.shop.domain.InvalidPaymentMethodException for an unknown payment method
    @Transactional
    public ShopOrderView collect(UUID tenantId, UUID userId, UUID id, @Nullable String method, long expectedVersion) {
        PaymentMethod payment = PaymentMethod.parse(method);
        ShopOrder order = order(id);
        order.requireVersion(expectedVersion);
        order.collect(payment, userId, clock.instant());
        stock.consumeAll(ReservationSource.SHOP_ORDER, order.id(), userId);
        return view(tenantId, orders.save(order));
    }

    /// PLACED or READY → CANCELLED with a reason the customer is told; the stock is released.
    ///
    /// @throws com.asp.platform.shop.domain.CancelReasonRequiredException without a reason
    @Transactional
    public ShopOrderView cancel(UUID tenantId, UUID id, @Nullable String reason, long expectedVersion) {
        ShopOrder order = order(id);
        order.requireVersion(expectedVersion);
        order.cancelByStaff(reason, clock.instant());
        stock.releaseAll(ReservationSource.SHOP_ORDER, order.id());
        ShopOrder saved = orders.save(order);
        announce(tenantId, saved, OrderStatusChanged.Change.CANCELLED, saved.cancelReason(), null);
        return view(tenantId, saved);
    }

    /// Ids of the current workshop's open orders whose hold ended before `now`, earliest hold first. Run it inside
    /// the workshop's [com.asp.platform.shared.TenantScope] (see [ShopOrderExpiry]).
    @Transactional(readOnly = true)
    public List<UUID> dueIds(Instant now, int limit) {
        return orders.findDueIds(now, limit);
    }

    /// Expires one order and releases its stock, in its own transaction, so a conflict with the front desk (who
    /// collected or cancelled it meanwhile) costs only this order. Run it inside the workshop's
    /// [com.asp.platform.shared.TenantScope].
    ///
    /// @return false when the order is no longer due
    @Transactional
    public boolean expire(UUID tenantId, UUID id, Instant now) {
        ShopOrder order = order(id);
        if (!order.isDue(now)) {
            return false;
        }
        order.expire(now);
        stock.releaseAll(ReservationSource.SHOP_ORDER, order.id());
        announce(tenantId, orders.save(order), OrderStatusChanged.Change.EXPIRED, null, null);
        return true;
    }

    /// Tells the customer after the commit, when they left an e-mail address.
    private void announce(UUID tenantId, ShopOrder order, OrderStatusChanged.Change change, @Nullable String reason,
            @Nullable String pickupNote) {
        String email = order.contact().email();
        if (email != null) {
            events.publishEvent(new OrderStatusChanged(tenantId, order.number(), order.contact().name(), email,
                    order.language(), change, reason, pickupNote, order.holdUntil()));
        }
    }

    private ShopSettings currentSettings() {
        return settings.find().orElseGet(() -> ShopSettings.defaults(UUID.randomUUID()));
    }

    private ShopOrder order(UUID id) {
        return orders.findById(id).orElseThrow(ShopOrderNotFoundException::new);
    }

    private ShopOrderView view(UUID tenantId, ShopOrder order) {
        return views(tenantId, List.of(order)).getFirst();
    }

    /// The names of who handed orders over, read once.
    private List<ShopOrderView> views(UUID tenantId, List<ShopOrder> found) {
        Map<UUID, String> names = found.stream().anyMatch(o -> o.collectedBy() != null)
                ? directory.memberNames(tenantId) : Map.of();
        return found.stream().map(o -> {
            UUID by = o.collectedBy();
            return new ShopOrderView(o, by == null ? null : names.get(by));
        }).toList();
    }
}

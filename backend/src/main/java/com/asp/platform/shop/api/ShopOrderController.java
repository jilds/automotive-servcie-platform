package com.asp.platform.shop.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;

import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.asp.platform.shared.CurrentUser;
import com.asp.platform.shop.application.ShopOrderService;
import com.asp.platform.shop.domain.ShopOrderStatus;

/// The front desk's shop orders. Owner and front desk only: orders carry the customer's contact data and prices,
/// which mechanics never get. Every decision sends the version it read (409 `stale-data`).
@RestController
@RequestMapping("/api/v1/shop-orders")
class ShopOrderController {

    private final ShopOrderService orders;

    ShopOrderController(ShopOrderService orders) {
        this.orders = orders;
    }

    /// @param from first day of placement (workshop's time zone), default `to` − 30 days
    /// @param to   last day, included, default today; at most 124 days after `from` (400 `validation`)
    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
    List<ShopOrderSummary> list(CurrentUser user,
            @RequestParam(required = false) @Nullable ShopOrderStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate to) {
        return orders.list(user.tenantId(), from, to, status).stream().map(ShopOrderSummary::of).toList();
    }

    /// `{ "count": 2 }`: orders waiting to be prepared.
    @GetMapping("/pending-count")
    @PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
    Map<String, Long> pendingCount() {
        return Map.of("count", orders.pendingCount());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
    ShopOrderResponse get(CurrentUser user, @PathVariable UUID id) {
        return ShopOrderResponse.of(orders.get(user.tenantId(), id));
    }

    @PostMapping("/{id}/ready")
    @PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
    ShopOrderResponse ready(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody VersionRequest request) {
        return ShopOrderResponse.of(orders.ready(user.tenantId(), id, request.requiredVersion()));
    }

    /// 400 `invalid-payment-method` unless CASH, CARD, MULTIBANCO or MBWAY.
    @PostMapping("/{id}/collect")
    @PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
    ShopOrderResponse collect(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody CollectRequest request) {
        return ShopOrderResponse.of(orders.collect(user.tenantId(), user.userId(), id, request.paymentMethod(),
                request.requiredVersion()));
    }

    /// 400 `cancel-reason-required` without a reason.
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
    ShopOrderResponse cancel(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody CancelRequest request) {
        return ShopOrderResponse.of(orders.cancel(user.tenantId(), id, request.reason(), request.requiredVersion()));
    }
}

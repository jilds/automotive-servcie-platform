package com.asp.platform.stock.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.asp.platform.shared.CurrentUser;
import com.asp.platform.stock.application.PurchaseOrderService;
import com.asp.platform.stock.domain.PurchaseOrderStatus;

/// Purchase orders of parts: the owner and the front desk prepare them, send them and receive the parts.
@RestController
@RequestMapping("/api/v1/stock/purchase-orders")
@PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
class PurchaseOrderController {

    private final PurchaseOrderService orders;

    PurchaseOrderController(PurchaseOrderService orders) {
        this.orders = orders;
    }

    /// Newest first. @param status only this status; all when absent
    @GetMapping
    List<PurchaseOrderResponse> list(@RequestParam(required = false) @Nullable PurchaseOrderStatus status) {
        return orders.list(status).stream().map(PurchaseOrderResponse::of).toList();
    }

    @GetMapping("/{id}")
    PurchaseOrderResponse get(@PathVariable UUID id) {
        return PurchaseOrderResponse.of(orders.get(id));
    }

    /// A new draft. The products must belong to the workshop (404 `product-not-found`) and still be sold
    /// (409 `product-inactive`).
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PurchaseOrderResponse create(@Valid @RequestBody PurchaseOrderRequest request) {
        return PurchaseOrderResponse.of(orders.create(request.toDetails()));
    }

    /// Drafts only (409 `purchase-order-locked` otherwise); needs the version the editor read.
    @PutMapping("/{id}")
    PurchaseOrderResponse update(@PathVariable UUID id,
            @Validated(OnUpdate.class) @RequestBody PurchaseOrderRequest request) {
        return PurchaseOrderResponse.of(orders.update(id, request.toDetails(), request.requiredVersion()));
    }

    /// The draft was sent to the supplier.
    @PostMapping("/{id}/order")
    PurchaseOrderResponse order(@PathVariable UUID id) {
        return PurchaseOrderResponse.of(orders.markOrdered(id));
    }

    /// The parts arrived: adds every line to stock. A second receipt answers 409 `illegal-purchase-order-status`.
    @PostMapping("/{id}/receive")
    PurchaseOrderResponse receive(CurrentUser user, @PathVariable UUID id) {
        return PurchaseOrderResponse.of(orders.receive(id, user.userId()));
    }
}

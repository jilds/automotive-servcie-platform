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
import com.asp.platform.stock.application.StockService;

/// The workshop's parts and their stock. Everyone may look a part up (mechanics: name, SKU, bin and available
/// quantity only); the owner and the front desk see cost, prices and movements and adjust stock; only the owner
/// creates and changes products.
@RestController
@RequestMapping("/api/v1/stock")
class ProductController {

    private final StockService stock;

    ProductController(StockService stock) {
        this.stock = stock;
    }

    /// @param q          part of the SKU, name or brand
    /// @param activeOnly true (default) leaves out products that are no longer sold
    /// @param lowOnly    only products with less available than their minimum stock
    @GetMapping("/products")
    List<ProductResponse> list(CurrentUser user, @RequestParam(required = false) @Nullable String q,
            @RequestParam(defaultValue = "true") boolean activeOnly,
            @RequestParam(defaultValue = "false") boolean lowOnly) {
        return stock.products(q, activeOnly, lowOnly).stream()
                .map(view -> ProductResponse.of(view, user.canSeePrices()))
                .toList();
    }

    @GetMapping("/products/{id}")
    ProductResponse get(CurrentUser user, @PathVariable UUID id) {
        return ProductResponse.of(stock.product(id), user.canSeePrices());
    }

    /// A new product has nothing on hand; a duplicate SKU answers 409 `duplicate-sku`.
    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OWNER')")
    ProductResponse create(@Valid @RequestBody ProductRequest request) {
        return ProductResponse.of(stock.create(request.toDetails()), true);
    }

    /// Needs the version the editor read; someone else's newer save answers 409 `stale-data`.
    @PutMapping("/products/{id}")
    @PreAuthorize("hasRole('OWNER')")
    ProductResponse update(@PathVariable UUID id, @Validated(OnUpdate.class) @RequestBody ProductRequest request) {
        return ProductResponse.of(stock.update(id, request.toDetails(), request.requiredVersion()), true);
    }

    /// The product's stock movements, newest first (the last 200).
    @GetMapping("/products/{id}/movements")
    @PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
    List<MovementResponse> movements(CurrentUser user, @PathVariable UUID id) {
        return stock.movements(user.tenantId(), id).stream().map(MovementResponse::of).toList();
    }

    /// Corrects the quantity on hand with a reason; taking away more than is on hand answers 409
    /// `insufficient-stock`.
    @PostMapping("/products/{id}/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
    ProductResponse adjust(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody AdjustmentRequest request) {
        return ProductResponse.of(stock.adjust(id, request.quantity(), request.reasonOrBlank(), user.userId()), true);
    }

    /// Active products with less available than their minimum stock.
    @GetMapping("/low-stock")
    @PreAuthorize("hasAnyRole('OWNER','FRONT_DESK')")
    List<ProductResponse> lowStock() {
        return stock.products(null, true, true).stream().map(view -> ProductResponse.of(view, true)).toList();
    }
}

package com.asp.platform.shop.api;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import jakarta.validation.Valid;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.asp.platform.shared.PrivacyConsentRequiredException;
import com.asp.platform.shared.PublicWorkshopRef;
import com.asp.platform.shared.StatusTokens;
import com.asp.platform.shop.application.PublicShopService;
import com.asp.platform.shop.domain.ShopSettings;

/// The shop pages of a workshop's public site (ADR 0004): anonymous, served for the workshop of the slug, 404
/// `add-on-not-available` while its shop add-on is off.
@RestController
@RequestMapping("/api/v1/public/{slug}/shop")
class PublicShopController {

    private static final Logger log = LoggerFactory.getLogger(PublicShopController.class);

    private final PublicShopService shop;
    private final Clock clock;

    PublicShopController(PublicShopService shop, Clock clock) {
        this.shop = shop;
        this.clock = clock;
    }

    /// @param q part of the name, brand or SKU, at most 60 characters (400 `validation` on `q`)
    @GetMapping("/products")
    List<PublicProductResponse> products(PublicWorkshopRef workshop, @RequestParam(required = false) @Nullable String q) {
        workshop.requireShop();
        return shop.products(q).stream().map(PublicProductResponse::of).toList();
    }

    /// The privacy consent is checked before the hidden `website` field: a filled one is a bot, answered like a
    /// success with nothing stored or reserved.
    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    ShopReceiptResponse place(PublicWorkshopRef workshop, @Valid @RequestBody PublicShopOrderRequest request) {
        workshop.requireShop();
        if (!request.acceptedPrivacy()) {
            throw new PrivacyConsentRequiredException();
        }
        if (request.honeypotFilled()) {
            log.info("Public shop order dropped: hidden field filled tenant={}", workshop.tenantId());
            return new ShopReceiptResponse(ThreadLocalRandom.current().nextInt(1, 1000), StatusTokens.generate(), 0,
                    clock.instant().plus(Duration.ofDays(ShopSettings.DEFAULT_HOLD_DAYS)), workshop.timeZone().getId());
        }
        return ShopReceiptResponse.of(shop.place(workshop.tenantId(), workshop.country(), request.toCommand()),
                workshop.timeZone());
    }

    @GetMapping("/orders/{token}")
    PublicShopOrderResponse status(PublicWorkshopRef workshop, @PathVariable String token) {
        workshop.requireShop();
        return PublicShopOrderResponse.of(shop.status(token, workshop.name(), workshop.timeZone()));
    }

    /// While the order is PLACED only (409 `shop-order-not-open` afterwards).
    @PostMapping("/orders/{token}/cancel")
    PublicShopOrderResponse cancel(PublicWorkshopRef workshop, @PathVariable String token) {
        workshop.requireShop();
        return PublicShopOrderResponse.of(shop.cancel(token, workshop.name(), workshop.timeZone()));
    }
}

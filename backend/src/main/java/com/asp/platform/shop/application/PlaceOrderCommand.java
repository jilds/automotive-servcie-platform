package com.asp.platform.shop.application;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/// An order from the public page, already validated for shape (1 to 20 lines, quantities 1 to 20, each product
/// once, a one-line name, a phone or an e-mail); the privacy notice was accepted.
///
/// @param language the page language (`pt-PT`, `en`, `pt-BR`) for the e-mails, or null
public record PlaceOrderCommand(List<Line> lines, String name, @Nullable String phone, @Nullable String email,
        @Nullable String nif, boolean marketingConsent, @Nullable String language) {

    public PlaceOrderCommand {
        lines = List.copyOf(lines);
    }

    public record Line(UUID productId, int quantity) {
    }
}

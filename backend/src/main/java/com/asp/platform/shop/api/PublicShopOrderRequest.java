package com.asp.platform.shop.api;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.asp.platform.shared.OneLineName;
import com.asp.platform.shared.PhoneOrEmail;
import com.asp.platform.shop.application.PlaceOrderCommand;
import com.asp.platform.shop.domain.ShopContact;
import com.asp.platform.shop.domain.ShopOrder;
import com.asp.platform.shop.domain.ShopOrderLine;

/// `POST /public/{slug}/shop/orders`: 1 to 20 lines (quantity 1 to 20, each product once, all reported on
/// `errors.lines`), the contact (a one-line name, at least a phone or an e-mail), an optional NIF, the consents, the
/// hidden `website` field, which people leave empty, and the page language for the e-mails.
@PhoneOrEmail
record PublicShopOrderRequest(
        @NotEmpty @Size(max = ShopOrder.MAX_LINES) List<@NotNull Line> lines,
        // The name goes into the mail to the given address (see OneLineName).
        @NotBlank @Size(max = ShopContact.MAX_NAME) @OneLineName String name,
        @Nullable @Size(max = ShopContact.MAX_PHONE)
        @Pattern(regexp = "[0-9 +()\\-]*", message = "Digits, spaces and + ( ) - only") String phone,
        @Nullable @Email @Size(max = ShopContact.MAX_EMAIL) String email,
        @Nullable @Size(max = ShopContact.MAX_NIF) String nif,
        @Nullable Boolean privacyAccepted,
        @Nullable Boolean marketingConsent,
        @Nullable @Size(max = 500) String website,
        @Nullable @Pattern(regexp = "pt-PT|en|pt-BR", message = "pt-PT, en or pt-BR") String language)
        implements PhoneOrEmail.Subject {

    record Line(@Nullable UUID productId, @Nullable Integer quantity) {
    }

    /// Reported as `errors.lines`: every line names a product and 1 to 20 units, each product at most once.
    @AssertTrue(message = "Each line names a product once, with 1 to 20 units")
    boolean isLines() {
        if (lines == null) {
            return true;
        }
        Set<UUID> products = new HashSet<>();
        for (Line line : lines) {
            if (line == null) {
                continue;
            }
            UUID product = line.productId();
            Integer quantity = line.quantity();
            if (product == null || quantity == null || quantity < 1 || quantity > ShopOrderLine.MAX_QUANTITY
                    || !products.add(product)) {
                return false;
            }
        }
        return true;
    }

    boolean honeypotFilled() {
        String w = website;
        return w != null && !w.isBlank();
    }

    boolean acceptedPrivacy() {
        return Boolean.TRUE.equals(privacyAccepted);
    }

    /// Call after validation: every line has a product and a quantity.
    PlaceOrderCommand toCommand() {
        List<PlaceOrderCommand.Line> commandLines = lines.stream()
                .map(l -> new PlaceOrderCommand.Line(require(l.productId()), require(l.quantity())))
                .toList();
        return new PlaceOrderCommand(commandLines, name, phone, email, nif, Boolean.TRUE.equals(marketingConsent),
                language);
    }

    private static <T> T require(@Nullable T value) {
        if (value == null) {
            throw new IllegalStateException("checked by isLines()");
        }
        return value;
    }
}

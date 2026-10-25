package com.asp.platform.identity.application;

import org.jspecify.annotations.Nullable;

import com.asp.platform.identity.domain.AddOns;
import com.asp.platform.identity.domain.BrandColor;
import com.asp.platform.identity.domain.Tenant;

/// A workshop's theme as the app applies it, plus the add-ons the signed-in shell shows menus for. The public login
/// page gets the presentation fields only (`BrandingResponse.ofPublic`).
///
/// @param name             the display name, or the workshop name when none is set
/// @param primaryTextColor `#ffffff` or `#000000`, whichever reaches 4.5:1 on the primary colour; null with it
/// @param logoHash         SHA-256 of the logo, or null when there is none
/// @param addOns           the add-ons switched on (online booking, online shop)
/// @param version          the workshop's version, sent back with the next theme change (optimistic locking)
public record BrandingView(String slug, String name, @Nullable String displayName, @Nullable String primaryColor,
        @Nullable String primaryTextColor, @Nullable String accentColor, @Nullable String logoHash, AddOns addOns,
        long version) {

    static BrandingView of(Tenant tenant, @Nullable String logoHash) {
        BrandColor primary = tenant.branding().primaryColor();
        BrandColor accent = tenant.branding().accentColor();
        return new BrandingView(tenant.slug().value(), tenant.shownName(), tenant.branding().displayName(),
                primary == null ? null : primary.hex(),
                primary == null ? null : primary.textColor().hex(),
                accent == null ? null : accent.hex(),
                logoHash, tenant.addOns(), tenant.version());
    }
}

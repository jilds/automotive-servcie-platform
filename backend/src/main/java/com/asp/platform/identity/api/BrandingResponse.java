package com.asp.platform.identity.api;

import org.jspecify.annotations.Nullable;

import com.asp.platform.identity.application.BrandingView;
import com.asp.platform.identity.domain.AddOns;
import com.fasterxml.jackson.annotation.JsonInclude;

/// A workshop's theme. Public for `/branding/{slug}`, so it holds presentation only: no id, NIF, subscription or
/// contact data.
///
/// @param name             what to show: the display name, or the workshop name when none is set
/// @param displayName      the display name as set, or null
/// @param primaryTextColor text colour on primary buttons (`#ffffff` or `#000000`, whichever reaches 4.5:1)
/// @param logoUrl          versioned logo address, or null when the workshop has no logo
/// @param addOns           the add-ons switched on, for the signed-in shell's menus; left out of the public
///                         `/branding/{slug}` answer, which says nothing about the subscription
/// @param privacyUrl       the platform's privacy policy on the landing site (`app.site.privacy-url`), linked from
///                         the public booking page; null when unset. The same for every workshop
/// @param version          the workshop's version, sent back with the next theme change; a counter, nothing more
record BrandingResponse(String slug, String name, @Nullable String displayName, @Nullable String primaryColor,
        @Nullable String primaryTextColor, @Nullable String accentColor, @Nullable String logoUrl,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable AddOns addOns, @Nullable String privacyUrl,
        long version) {

    private static final int VERSION_LENGTH = 12;

    /// For the workshop's own members and the platform admin: with the add-ons.
    static BrandingResponse of(BrandingView v) {
        return build(v, v.addOns());
    }

    /// For the public branded login page: presentation only, no add-ons.
    static BrandingResponse ofPublic(BrandingView v) {
        return build(v, null);
    }

    private static BrandingResponse build(BrandingView v, @Nullable AddOns addOns) {
        String hash = v.logoHash();
        String logoUrl = hash == null ? null
                : "/api/v1/branding/" + v.slug() + "/logo?v=" + hash.substring(0, VERSION_LENGTH);
        return new BrandingResponse(v.slug(), v.name(), v.displayName(), v.primaryColor(), v.primaryTextColor(),
                v.accentColor(), logoUrl, addOns, v.privacyUrl(), v.version());
    }
}

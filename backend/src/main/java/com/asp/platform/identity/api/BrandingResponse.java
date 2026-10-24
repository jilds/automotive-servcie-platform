package com.asp.platform.identity.api;

import org.jspecify.annotations.Nullable;

import com.asp.platform.identity.application.BrandingView;

/// A workshop's theme. Public for `/branding/{slug}`, so it holds presentation only: no id, NIF, subscription or
/// contact data.
///
/// @param name             what to show: the display name, or the workshop name when none is set
/// @param displayName      the display name as set, or null
/// @param primaryTextColor text colour on primary buttons (`#ffffff` or `#000000`, whichever reaches 4.5:1)
/// @param logoUrl          versioned logo address, or null when the workshop has no logo
/// @param version          the workshop's version, sent back with the next theme change; a counter, nothing more
record BrandingResponse(String slug, String name, @Nullable String displayName, @Nullable String primaryColor,
        @Nullable String primaryTextColor, @Nullable String accentColor, @Nullable String logoUrl, long version) {

    private static final int VERSION_LENGTH = 12;

    static BrandingResponse of(BrandingView v) {
        String hash = v.logoHash();
        String logoUrl = hash == null ? null
                : "/api/v1/branding/" + v.slug() + "/logo?v=" + hash.substring(0, VERSION_LENGTH);
        return new BrandingResponse(v.slug(), v.name(), v.displayName(), v.primaryColor(), v.primaryTextColor(),
                v.accentColor(), logoUrl, v.version());
    }
}

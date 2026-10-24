package com.asp.platform.identity.api;

import java.io.IOException;
import java.time.Duration;

import jakarta.validation.Valid;

import org.jspecify.annotations.Nullable;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartFile;

import com.asp.platform.identity.application.BrandingService;
import com.asp.platform.identity.domain.Logo;
import com.asp.platform.shared.CurrentUser;

/// Workshop themes: public by slug for the branded login page, the signed-in user's own workshop, and the owner's
/// changes. An owner can only ever address their own workshop: the tenant comes from the token.
@RestController
class BrandingController {

    /// Logo URLs carry the content hash (`?v=`), so a new logo is a new URL; an hour keeps old URLs cheap too.
    static final CacheControl LOGO_CACHE = CacheControl.maxAge(Duration.ofHours(1)).cachePublic();
    /// An SVG opened on its own runs in a sandbox with nothing allowed but its own inline styles.
    private static final String SVG_CSP = "default-src 'none'; style-src 'unsafe-inline'; sandbox";

    private final BrandingService branding;

    BrandingController(BrandingService branding) {
        this.branding = branding;
    }

    @GetMapping("/api/v1/branding/{slug}")
    BrandingResponse bySlug(@PathVariable String slug) {
        return BrandingResponse.of(branding.bySlug(slug));
    }

    @GetMapping("/api/v1/branding/{slug}/logo")
    @Nullable ResponseEntity<byte[]> logo(@PathVariable String slug, WebRequest request) {
        return logoResponse(branding.logo(slug), request);
    }

    /// Any member of the workshop, so the app can apply the theme after login.
    @GetMapping("/api/v1/branding")
    BrandingResponse current(CurrentUser user) {
        return BrandingResponse.of(branding.current(user.tenantId()));
    }

    @PutMapping("/api/v1/workshop/branding")
    @PreAuthorize("hasRole('OWNER')")
    BrandingResponse update(CurrentUser user, @Valid @RequestBody BrandingRequest request) {
        return BrandingResponse.of(branding.update(user.tenantId(), request.toBranding(), request.version()));
    }

    /// Multipart upload, part name `file`. PNG, SVG or WEBP, at most 512 KB; the type is read from the content.
    @PutMapping(path = "/api/v1/workshop/branding/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('OWNER')")
    BrandingResponse uploadLogo(CurrentUser user, @RequestPart("file") MultipartFile file) throws IOException {
        return BrandingResponse.of(branding.uploadLogo(user.tenantId(), file.getBytes()));
    }

    @DeleteMapping("/api/v1/workshop/branding/logo")
    @PreAuthorize("hasRole('OWNER')")
    BrandingResponse removeLogo(CurrentUser user) {
        return BrandingResponse.of(branding.removeLogo(user.tenantId()));
    }

    /// The logo with a strong ETag (its SHA-256); answers 304 when the client already has it.
    static @Nullable ResponseEntity<byte[]> logoResponse(Logo logo, WebRequest request) {
        String etag = "\"" + logo.sha256() + "\"";
        if (request.checkNotModified(etag)) {
            return null;
        }
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .eTag(etag)
                .cacheControl(LOGO_CACHE)
                .contentType(MediaType.parseMediaType(logo.contentType()))
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"logo\"");
        if (Logo.SVG.equals(logo.contentType())) {
            response.header("Content-Security-Policy", SVG_CSP);
        }
        return response.body(logo.content());
    }
}

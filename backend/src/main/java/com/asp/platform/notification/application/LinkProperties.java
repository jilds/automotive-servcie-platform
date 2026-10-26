package com.asp.platform.notification.application;

import java.net.URI;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/// `app.public-url`: where people open the app, for the absolute links in mails (`https://app.asp.example`).
/// Required while mail is on.
@ConfigurationProperties("app")
record LinkProperties(@Nullable URI publicUrl) {

    /// The absolute link for a path of the app that starts with `/`.
    String link(String path) {
        URI base = publicUrl;
        if (base == null) {
            throw new IllegalStateException("app.public-url (ASP_PUBLIC_URL) is required while mail is on");
        }
        String root = base.toString();
        return (root.endsWith("/") ? root.substring(0, root.length() - 1) : root) + path;
    }
}

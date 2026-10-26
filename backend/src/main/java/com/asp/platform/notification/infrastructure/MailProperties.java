package com.asp.platform.notification.infrastructure;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/// `app.mail.*`. The SMTP server itself is `spring.mail.*` (`ASP_MAIL_HOST`, `ASP_MAIL_PORT`, …).
///
/// @param enabled false (the default): nothing is sent
/// @param from    the sender address, required while enabled, for example `ASP <noreply@asp.example>`
@ConfigurationProperties("app.mail")
record MailProperties(boolean enabled, @Nullable String from) {
}

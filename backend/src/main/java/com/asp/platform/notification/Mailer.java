package com.asp.platform.notification;

/// Sends one e-mail. Never throws for a delivery problem: it is logged (template only, never the address or the
/// content) and the caller carries on.
public interface Mailer {

    /// False when `app.mail.enabled` is off: [#send] then does nothing.
    boolean enabled();

    void send(MailMessage message);
}

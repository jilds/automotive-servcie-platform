package com.asp.platform.notification.infrastructure;

import java.nio.charset.StandardCharsets;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import com.asp.platform.notification.MailMessage;
import com.asp.platform.notification.Mailer;

/// [Mailer] over SMTP (Spring Mail, `spring.mail.*`), each mail as plain text with an HTML alternative. Off unless
/// `app.mail.enabled`; then the start fails without `app.mail.from` or `spring.mail.host`. Logs the template only:
/// never the address, the link or the content.
@Component
class SmtpMailer implements Mailer {

    private static final Logger log = LoggerFactory.getLogger(SmtpMailer.class);

    private final boolean enabled;
    private final @Nullable String from;
    private final @Nullable JavaMailSender sender;
    private final MailTemplates templates;

    SmtpMailer(MailProperties properties, ObjectProvider<JavaMailSender> sender, MailTemplates templates) {
        this.enabled = properties.enabled();
        this.from = properties.from();
        this.sender = sender.getIfAvailable();
        this.templates = templates;
        if (enabled && (from == null || from.isBlank() || !hasHost(this.sender))) {
            throw new IllegalStateException("app.mail.enabled is true but app.mail.from (ASP_MAIL_FROM) or "
                    + "spring.mail.host (ASP_MAIL_HOST) is not set");
        }
        log.info("Mail {}", enabled ? "on" : "off (app.mail.enabled=false)");
    }

    /// Spring Boot builds the sender as soon as `spring.mail.host` is present, also when it is empty.
    private static boolean hasHost(@Nullable JavaMailSender sender) {
        if (sender instanceof JavaMailSenderImpl impl) {
            String host = impl.getHost();
            return host != null && !host.isBlank();
        }
        return sender != null;
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public void send(MailMessage message) {
        JavaMailSender smtp = sender;
        String fromAddress = from;
        if (!enabled || smtp == null || fromAddress == null) {
            return;
        }
        try {
            MailTemplates.Rendered rendered = templates.render(message);
            MimeMessage mime = smtp.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(message.to());
            helper.setSubject(rendered.subject());
            helper.setText(rendered.text(), rendered.html());
            smtp.send(mime);
            log.info("Mail sent template={}", message.template());
        } catch (MessagingException | RuntimeException e) {
            // MailException included. The business change is committed already; a lost mail is logged, not retried.
            log.warn("Mail not sent template={} error={}", message.template(), e.getClass().getSimpleName());
        }
    }
}

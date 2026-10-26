package com.asp.platform.notification.infrastructure;

import org.springframework.context.MessageSource;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import com.asp.platform.notification.MailMessage;

/// Renders a [MailMessage] into its subject, plain text and HTML.
final class MailTemplates {

    private final TemplateEngine engine;
    private final MessageSource texts;

    MailTemplates(TemplateEngine engine, MessageSource texts) {
        this.engine = engine;
        this.texts = texts;
    }

    Rendered render(MailMessage message) {
        Context context = new Context(message.locale(), message.model());
        String file = message.template().file();
        Object[] subjectArguments = message.template().subjectArguments().stream()
                .map(message.model()::get)
                .toArray();
        String subject = texts.getMessage(file + ".subject", subjectArguments, message.locale());
        return new Rendered(subject, engine.process(file + ".txt", context).strip(),
                engine.process(file + ".html", context));
    }

    record Rendered(String subject, String text, String html) {
    }
}

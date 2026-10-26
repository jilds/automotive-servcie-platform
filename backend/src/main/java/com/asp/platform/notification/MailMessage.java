package com.asp.platform.notification;

import java.util.Locale;
import java.util.Map;

/// One e-mail to one recipient.
///
/// @param model the template's variables; `subject` arguments are [MailTemplate#subjectArguments] taken from it
public record MailMessage(String to, MailTemplate template, Locale locale, Map<String, Object> model) {

    public MailMessage {
        model = Map.copyOf(model);
    }

    @Override
    public String toString() {
        // The address and the model (links, names) stay out of logs.
        return "MailMessage[template=" + template + ", locale=" + locale + "]";
    }
}

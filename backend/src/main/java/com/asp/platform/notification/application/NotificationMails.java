package com.asp.platform.notification.application;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import com.asp.platform.booking.BookingRequested;
import com.asp.platform.booking.BookingStatusChanged;
import com.asp.platform.identity.ActivationLinkIssued;
import com.asp.platform.identity.LeadReceived;
import com.asp.platform.identity.PlatformTeam;
import com.asp.platform.identity.WorkshopDirectory;
import com.asp.platform.identity.WorkshopProfile;
import com.asp.platform.notification.MailMessage;
import com.asp.platform.notification.MailTemplate;
import com.asp.platform.notification.Mailer;
import com.asp.platform.notification.domain.MailLanguage;

/// Turns other modules' events into mails. Each listener runs only after the publishing transaction committed (a
/// rolled-back change sends nothing) and off the request thread (`@Async`), so a slow or failing mail server never
/// delays or undoes the business change. With mail off, nothing is read or sent.
///
/// The booking status token exists only in [BookingRequested], so only that first mail carries the status link; the
/// later mails say the new status and time.
@Component
class NotificationMails {

    private static final Logger log = LoggerFactory.getLogger(NotificationMails.class);

    private final Mailer mailer;
    private final WorkshopDirectory directory;
    private final PlatformTeam team;
    private final LinkProperties links;

    NotificationMails(Mailer mailer, WorkshopDirectory directory, PlatformTeam team, LinkProperties links) {
        if (mailer.enabled() && links.publicUrl() == null) {
            throw new IllegalStateException("app.public-url (ASP_PUBLIC_URL) is required while app.mail.enabled is "
                    + "true: mails carry absolute links");
        }
        this.mailer = mailer;
        this.directory = directory;
        this.team = team;
        this.links = links;
    }

    /// The ASP team works in Portuguese (Portugal), so the lead mail is in pt-PT.
    @Async
    @TransactionalEventListener
    void on(LeadReceived event) {
        if (!mailer.enabled()) {
            return;
        }
        safely(MailTemplate.LEAD_RECEIVED, () -> {
            Map<String, Object> model = new HashMap<>();
            model.put("name", event.name());
            model.put("workshop", event.workshopName());
            model.put("phone", nonNull(event.phone()));
            model.put("email", nonNull(event.email()));
            model.put("district", nonNull(event.district()));
            model.put("vehicleType", event.vehicleType());
            model.put("hasMessage", event.hasMessage());
            model.put("link", links.link("/admin/leads"));
            Locale locale = Locale.forLanguageTag("pt-PT");
            for (String admin : team.activeAdminEmails()) {
                mailer.send(new MailMessage(admin, MailTemplate.LEAD_RECEIVED, locale, model));
            }
        });
    }

    @Async
    @TransactionalEventListener
    void on(ActivationLinkIssued event) {
        if (!mailer.enabled()) {
            return;
        }
        safely(MailTemplate.ACTIVATION, () -> directory.profile(event.tenantId()).ifPresent(workshop -> {
            Locale locale = MailLanguage.of(null, workshop.country());
            Map<String, Object> model = base(event.name(), workshop);
            model.put("link", links.link(event.path()));
            model.put("expires", format(event.expiresAt(), workshop.timeZone(), locale, FormatStyle.MEDIUM));
            model.put("firstOwner", event.firstOwner());
            mailer.send(new MailMessage(event.email(), MailTemplate.ACTIVATION, locale, model));
        }));
    }

    @Async
    @TransactionalEventListener
    void on(BookingRequested event) {
        if (!mailer.enabled()) {
            return;
        }
        safely(MailTemplate.BOOKING_RECEIVED, () -> directory.profile(event.tenantId()).ifPresent(workshop -> {
            Locale locale = MailLanguage.of(event.language(), workshop.country());
            Map<String, Object> model = booking(event.name(), workshop, event.number(), event.slotStart(), locale);
            model.put("link", links.link("/w/" + workshop.slug() + "/booking/" + event.statusToken()));
            mailer.send(new MailMessage(event.email(), MailTemplate.BOOKING_RECEIVED, locale, model));
        }));
    }

    @Async
    @TransactionalEventListener
    void on(BookingStatusChanged event) {
        if (!mailer.enabled()) {
            return;
        }
        MailTemplate template = switch (event.change()) {
            case CONFIRMED -> MailTemplate.BOOKING_CONFIRMED;
            case MOVED -> MailTemplate.BOOKING_MOVED;
            case DECLINED -> MailTemplate.BOOKING_DECLINED;
        };
        safely(template, () -> directory.profile(event.tenantId()).ifPresent(workshop -> {
            Locale locale = MailLanguage.of(null, workshop.country());
            Map<String, Object> model = booking(event.name(), workshop, event.number(), event.slotStart(), locale);
            model.put("reason", nonNull(event.reason()));
            mailer.send(new MailMessage(event.email(), template, locale, model));
        }));
    }

    /// A failure before the send (the workshop lookup, a date format) is logged like a failed send: template only.
    private static void safely(MailTemplate template, Runnable prepareAndSend) {
        try {
            prepareAndSend.run();
        } catch (RuntimeException e) {
            log.warn("Mail not prepared template={} error={}", template, e.getClass().getSimpleName());
        }
    }

    private static Map<String, Object> booking(String name, WorkshopProfile workshop, int number, Instant slot,
            Locale locale) {
        Map<String, Object> model = base(name, workshop);
        // As text, so MessageFormat never writes "1,000".
        model.put("number", Integer.toString(number));
        model.put("when", format(slot, workshop.timeZone(), locale, FormatStyle.FULL));
        return model;
    }

    private static Map<String, Object> base(String name, WorkshopProfile workshop) {
        Map<String, Object> model = new HashMap<>();
        model.put("name", name);
        model.put("workshop", workshop.name());
        return model;
    }

    /// A date and time as people read it in the mail's language, in the workshop's time zone.
    static String format(Instant instant, ZoneId zone, Locale locale, FormatStyle dateStyle) {
        return DateTimeFormatter.ofLocalizedDateTime(dateStyle, FormatStyle.SHORT).withLocale(locale)
                .format(instant.atZone(zone));
    }

    private static String nonNull(@Nullable String value) {
        return value == null ? "" : value;
    }
}

/// E-mail to users and customers through SMTP (Spring Mail), from Thymeleaf text and HTML templates in pt-PT, en and
/// pt-BR. Off unless `app.mail.enabled` is true; then nothing is sent and the links stay on screen as before.
/// Public API: [Mailer] with [MailMessage] and [MailTemplate]. Listens, after the commit and off the request thread,
/// to [com.asp.platform.identity.ActivationLinkIssued], [com.asp.platform.booking.BookingRequested],
/// [com.asp.platform.booking.BookingStatusChanged] and [com.asp.platform.identity.LeadReceived] (to the platform admins
/// from [com.asp.platform.identity.PlatformTeam]), and reads the workshop's name, slug, country and time zone from
/// [com.asp.platform.identity.WorkshopDirectory]. No other module depends on it for those mails, so a mail problem
/// never undoes a business change.
@ApplicationModule(displayName = "Notification")
@NullMarked
package com.asp.platform.notification;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;

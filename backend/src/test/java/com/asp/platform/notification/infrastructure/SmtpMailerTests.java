package com.asp.platform.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Locale;
import java.util.Map;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import com.asp.platform.notification.MailMessage;
import com.asp.platform.notification.MailTemplate;

class SmtpMailerTests {

    private static final MailMessage MAIL = new MailMessage("ana@example.pt", MailTemplate.BOOKING_CONFIRMED,
            Locale.forLanguageTag("pt-PT"), Map.of("name", "Ana", "workshop", "Oficina", "number", "1", "when", "x"));

    private final MailTemplates templates = new MailTemplateConfig().mailTemplates();

    @Test
    void should_send_nothing_while_mail_is_off() {
        JavaMailSender sender = mock(JavaMailSender.class);
        SmtpMailer mailer = new SmtpMailer(new MailProperties(false, null), provider(sender), templates);

        mailer.send(MAIL);

        verify(sender, never()).createMimeMessage();
        verify(sender, never()).send(any(MimeMessage.class));
    }

    @Test
    void should_never_throw_when_the_server_refuses_the_mail() {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
        doThrow(new MailSendException("connection refused")).when(sender).send(any(MimeMessage.class));
        SmtpMailer mailer = new SmtpMailer(new MailProperties(true, "ASP <noreply@asp.test>"), provider(sender),
                templates);

        assertThatCode(() -> mailer.send(MAIL)).doesNotThrowAnyException();
        verify(sender).send(any(MimeMessage.class));
    }

    @Test
    void should_refuse_to_start_when_mail_is_on_without_a_sender_address_or_host() {
        JavaMailSenderImpl noHost = new JavaMailSenderImpl();
        noHost.setHost("");
        JavaMailSenderImpl host = new JavaMailSenderImpl();
        host.setHost("smtp.asp.test");

        assertThatIllegalStateException().isThrownBy(
                () -> new SmtpMailer(new MailProperties(true, null), provider(host), templates));
        assertThatIllegalStateException().isThrownBy(
                () -> new SmtpMailer(new MailProperties(true, "noreply@asp.test"), provider(noHost), templates));
        assertThatIllegalStateException().isThrownBy(
                () -> new SmtpMailer(new MailProperties(true, "noreply@asp.test"), provider(null), templates));
        assertThatCode(() -> new SmtpMailer(new MailProperties(false, null), provider(null), templates))
                .doesNotThrowAnyException();
    }

    private static org.springframework.beans.factory.ObjectProvider<JavaMailSender> provider(JavaMailSender sender) {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        if (sender != null) {
            beans.addBean("mailSender", sender);
        }
        return beans.getBeanProvider(JavaMailSender.class);
    }
}

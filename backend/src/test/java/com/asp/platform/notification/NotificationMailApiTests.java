package com.asp.platform.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import jakarta.mail.Message;
import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.asp.platform.identity.application.IdentityService;
import com.asp.platform.identity.application.InviteUserCommand;
import com.asp.platform.shared.Role;
import com.asp.platform.shared.TenantScope;
import com.asp.platform.workshop.QuoteTestSupport;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.jayway.jsonpath.JsonPath;

/// Mail on, against an in-memory SMTP server: who gets which mail, in which language, with which link, and that a
/// change that is rolled back sends nothing. The `@Async` listeners run on a synchronous executor here, so a mail
/// that should exist is there when the request returns and one that should not can be checked without waiting.
@TestPropertySource(properties = {
        "app.mail.enabled=true",
        "app.mail.from=ASP <noreply@asp.test>",
        "app.public-url=https://app.asp.test/",
        "spring.mail.host=127.0.0.1",
        "spring.mail.port=3025"})
class NotificationMailApiTests extends QuoteTestSupport {

    @RegisterExtension
    static final GreenMailExtension SMTP = new GreenMailExtension(ServerSetupTest.SMTP);

    @TestConfiguration(proxyBeanMethods = false)
    static class SynchronousMails {

        /// Picked by `@Async` as the only primary task executor.
        @Bean
        @Primary
        TaskExecutor synchronousTaskExecutor() {
            return new SyncTaskExecutor();
        }
    }

    @Autowired
    IdentityService identity;

    @Autowired
    PlatformTransactionManager transactions;

    @Test
    void should_mail_the_first_owner_their_activation_link_in_the_workshops_language() throws Exception {
        String ownerEmail = uniqueEmail("dono");
        String body = provisionWorkshop("Oficina Correio", ownerEmail).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String path = JsonPath.read(body, "$.activationPath");

        MimeMessage mail = mailTo(ownerEmail);

        assertThat(mail.getSubject()).isEqualTo("O seu acesso a Oficina Correio no ASP");
        assertThat(mail.getFrom()[0].toString()).contains("noreply@asp.test");
        // The trailing slash of app.public-url is not doubled.
        assertThat(decodedText(mail)).contains("https://app.asp.test" + path, "Definir a palavra-passe");
    }

    @Test
    void should_mail_an_invited_team_member() throws Exception {
        Workshop w = workshop("Oficina Convite", false);
        String email = uniqueEmail("rececao");

        invite(w.owner(), "FRONT_DESK", email);

        assertThat(decodedText(mailTo(email))).contains("Recebeu um convite para entrar em");
    }

    @Test
    void should_send_nothing_when_the_change_is_rolled_back() throws Exception {
        Workshop w = workshop("Oficina Repetida", false);
        String email = uniqueEmail("anulado");
        UUID tenant = UUID.fromString(w.id());

        // The link is issued (and the event published), then the transaction rolls back.
        TenantScope.runAs(tenant, () -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            identity.invite(tenant, new InviteUserCommand("Anulado", email, Role.MECHANIC));
            status.setRollbackOnly();
        }));

        assertThat(mailsTo(email)).isEmpty();
    }

    @Test
    void should_mail_the_status_link_in_the_page_language_then_each_decision() throws Exception {
        Workshop w = workshop("Oficina Agenda", true);
        String service = service(w.owner(), "Revisão", 9000, true, true);
        openEveryDay(w, 2);
        List<String> slots = freeSlots(w, tomorrow());
        String email = uniqueEmail("cliente");

        String token = JsonPath.read(publicCall(post(w.publicPath("/bookings")).content(body(slots.get(0), email,
                "en", service))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
                "$.statusToken");

        MimeMessage received = mailTo(email);
        assertThat(received.getSubject()).isEqualTo("Booking request no. 1 at Oficina Agenda");
        assertThat(decodedText(received)).contains("See or cancel your booking");
        assertThat(decodedText(received)).contains("https://app.asp.test/w/" + w.slug() + "/booking/" + token);

        String id = bookingId(w, 1);
        call(post("/api/v1/bookings/" + id + "/confirm").content("{\"version\":0}"), w.owner())
                .andExpect(status().isOk());

        MimeMessage confirmed = awaitMails(email, 2).get(1);
        // Later mails use the workshop's language and carry no token.
        assertThat(confirmed.getSubject()).isEqualTo("Marcação n.º 1 em Oficina Agenda confirmada");
        assertThat(decodedText(confirmed)).doesNotContain(token);
    }

    @Test
    void should_tell_the_customer_why_a_booking_was_declined() throws Exception {
        Workshop w = workshop("Oficina Recusa", true);
        String service = service(w.owner(), "Pneus", 5000, true, true);
        openEveryDay(w, 2);
        String email = uniqueEmail("recusa");
        publicCall(post(w.publicPath("/bookings")).content(body(freeSlots(w, tomorrow()).get(0), email, null,
                service))).andExpect(status().isCreated());
        mailTo(email);

        call(post("/api/v1/bookings/" + bookingId(w, 1) + "/decline")
                .content("{\"reason\":\"Sem pneus em stock\",\"version\":0}"), w.owner())
                .andExpect(status().isOk());

        MimeMessage declined = awaitMails(email, 2).get(1);
        assertThat(declined.getSubject()).isEqualTo("Marcação n.º 1 em Oficina Recusa não aceite");
        assertThat(decodedText(declined)).contains("Motivo: Sem pneus em stock");
    }

    @Test
    void should_send_nothing_for_a_booking_with_a_phone_only() throws Exception {
        Workshop w = workshop("Oficina Telefone", true);
        String service = service(w.owner(), "Óleo", 4000, true, true);
        openEveryDay(w, 2);
        publicCall(post(w.publicPath("/bookings")).content("""
                {"serviceIds":["%s"],"slotStart":"%s","name":"Rui","phone":"912345678","plate":"AA-00-AA",
                 "privacyAccepted":true,"marketingConsent":false,"website":""}"""
                .formatted(service, freeSlots(w, tomorrow()).get(0)))).andExpect(status().isCreated());

        assertThat(Arrays.stream(SMTP.getReceivedMessages()).filter(m -> subject(m).contains("Oficina Telefone")
                && (subject(m).contains("Pedido de marcação") || subject(m).contains("Booking request")))).isEmpty();
    }

    @Test
    void should_refuse_a_name_with_a_link_before_anything_is_mailed() throws Exception {
        Workshop w = workshop("Oficina Ligacao", true);
        String service = service(w.owner(), "Travões", 4000, true, true);
        openEveryDay(w, 2);
        String email = uniqueEmail("vitima");

        publicCall(post(w.publicPath("/bookings")).content(body(freeSlots(w, tomorrow()).get(0), email, null, service)
                .replace("\"Ana Silva\"", "\"Ganhou! Abra https://evil.example\""))).andExpect(status().isBadRequest());

        assertThat(mailsTo(email)).isEmpty();
    }

    @Test
    void should_tell_the_platform_admins_about_a_demo_request() throws Exception {
        String workshop = "Oficina Lead " + UUID.randomUUID().toString().substring(0, 6);

        mvc.perform(post("/api/v1/public-site/leads").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Ana Silva","workshopName":"%s","email":"ana@oficina.example","district":"Porto",
                         "vehicleType":"MOTORCYCLE","message":"Queremos uma demonstração","privacyAccepted":true,
                         "marketingConsent":true,"website":""}""".formatted(workshop))
                .with(r -> {
                    r.setRemoteAddr("10.77.0.1");
                    return r;
                }))
                .andExpect(status().isCreated());

        MimeMessage mail = mailsTo(PLATFORM_ADMIN_EMAIL).stream()
                .filter(m -> subject(m).equals("Novo pedido de proposta: " + workshop)).findFirst().orElseThrow();
        // The free-text message stays out of the mail (it could carry a link); the mail says there is one.
        assertThat(decodedText(mail)).contains("Ana Silva", "ana@oficina.example", "Porto", "Motociclos",
                "O pedido tem uma mensagem", "https://app.asp.test/admin/leads")
                .doesNotContain("Queremos uma demonstração");
    }

    @Test
    void should_mail_the_shop_status_link_then_ready_and_cancel_in_the_page_language() throws Exception {
        Shop s = shop("Loja Correio", true);
        String filter = product(s, "FLT-OL-01", "Filtro de óleo", 1200, true, 10);
        call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/shop/settings")
                .content("{\"holdDays\":3,\"pickupNote\":\"Ao balcão, 9h-18h\",\"version\":0}"), s.owner())
                .andExpect(status().isOk());
        String email = uniqueEmail("comprador");
        String body = orderBody(filter, 2).replace("ana@example.pt", email).replace("\"pt-PT\"", "\"en\"");

        String token = JsonPath.read(order(s, body).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString(), "$.statusToken");

        MimeMessage received = mailTo(email);
        assertThat(received.getSubject()).isEqualTo("Order no. 1 at Loja Correio");
        assertThat(decodedText(received)).contains("See or cancel your order", "€29.52",
                "https://app.asp.test/w/" + s.slug() + "/shop/" + token);

        call(post("/api/v1/shop-orders/" + orderId(s, 1) + "/ready").content("{\"version\":0}"), s.owner())
                .andExpect(status().isOk());
        MimeMessage ready = awaitMails(email, 2).get(1);
        // Later mails keep the page language and carry no token.
        assertThat(ready.getSubject()).isEqualTo("Order no. 1 at Loja Correio is ready");
        assertThat(decodedText(ready)).contains("From the workshop: Ao balcão, 9h-18h").doesNotContain(token);

        call(post("/api/v1/shop-orders/" + orderId(s, 1) + "/cancel")
                .content("{\"reason\":\"Produto danificado\",\"version\":1}"), s.owner()).andExpect(status().isOk());
        MimeMessage cancelled = awaitMails(email, 3).get(2);
        assertThat(cancelled.getSubject()).isEqualTo("Order no. 1 at Loja Correio cancelled");
        assertThat(decodedText(cancelled)).contains("Reason: Produto danificado");
    }

    @Test
    void should_send_nothing_when_the_customer_cancels_a_shop_order() throws Exception {
        Shop s = shop("Loja Desiste", true);
        String filter = product(s, "FLT-OL-01", "Filtro de óleo", 1200, true, 10);
        String email = uniqueEmail("desiste");
        String token = JsonPath.read(order(s, orderBody(filter, 1).replace("ana@example.pt", email))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.statusToken");
        assertThat(mailTo(email).getSubject()).isEqualTo("Encomenda n.º 1 em Loja Desiste");

        publicCall(post(s.publicPath("/orders/" + token + "/cancel"))).andExpect(status().isOk());

        assertThat(mailsTo(email)).hasSize(1);
    }

    @Test
    void should_mail_the_quote_link_in_the_workshops_language_only_to_a_customer_with_an_email() throws Exception {
        Shop g = garage("Oficina Orcamento");
        String email = uniqueEmail("orcamento");
        String wo = workOrder(g, vehicle(g, email), null);
        labour(g, wo, "Mudança de óleo", 4000, 23);
        int orderNumber = JsonPath.read(call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/v1/work-orders/" + wo), g.owner()).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(), "$.number");

        String token = tokenOfQuote(sendQuote(g, wo));

        MimeMessage mail = mailTo(email);
        assertThat(mail.getSubject())
                .isEqualTo("Orçamento da ordem de reparação n.º " + orderNumber + " em Oficina Orcamento");
        assertThat(decodedText(mail)).contains("Ver e aprovar o orçamento", "49,20",
                "https://app.asp.test/w/" + g.slug() + "/quote/" + token);

        // A customer without an e-mail gets nothing; the quote is still sent.
        int before = SMTP.getReceivedMessages().length;
        String noMail = workOrder(g, vehicle(g, (String) null), null);
        labour(g, noMail, "Revisão", 5000, 23);
        send(g.owner(), noMail).andExpect(status().isCreated());
        assertThat(SMTP.getReceivedMessages()).hasSize(before);
    }

    private static String subject(MimeMessage message) {
        try {
            return String.valueOf(message.getSubject());
        } catch (jakarta.mail.MessagingException e) {
            return "";
        }
    }

    private static String body(String slotStart, String email, String language, String serviceId) {
        return """
                {"serviceIds":["%s"],"slotStart":"%s","name":"Ana Silva","email":"%s","plate":"AA-00-AA",
                 "privacyAccepted":true,"marketingConsent":false,"website":""%s}"""
                .formatted(serviceId, slotStart, email, language == null ? "" : ",\"language\":\"" + language + "\"");
    }

    private static MimeMessage mailTo(String email) {
        return awaitMails(email, 1).getFirst();
    }

    private static List<MimeMessage> awaitMails(String email, int count) {
        await().atMost(Duration.ofSeconds(10)).until(() -> mailsTo(email).size() >= count);
        return mailsTo(email);
    }

    private static List<MimeMessage> mailsTo(String email) {
        return Arrays.stream(SMTP.getReceivedMessages()).filter(m -> {
            try {
                return Arrays.stream(m.getRecipients(Message.RecipientType.TO))
                        .anyMatch(a -> a.toString().toLowerCase(Locale.ROOT).contains(email.toLowerCase(Locale.ROOT)));
            } catch (jakarta.mail.MessagingException e) {
                return false;
            }
        }).toList();
    }

    /// The plain-text part, decoded.
    private static String decodedText(MimeMessage mail) throws Exception {
        jakarta.mail.Multipart outer = (jakarta.mail.Multipart) mail.getContent();
        return textOf(outer);
    }

    private static String textOf(jakarta.mail.Multipart multipart) throws Exception {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < multipart.getCount(); i++) {
            Object content = multipart.getBodyPart(i).getContent();
            if (content instanceof jakarta.mail.Multipart nested) {
                text.append(textOf(nested));
            } else if (content instanceof String s) {
                text.append(s);
            }
        }
        return text.toString();
    }
}

package com.asp.platform.notification.infrastructure;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/// A Thymeleaf engine for the mail templates only (`classpath:mail/*.html` and `*.txt`), with its own texts
/// (`mail/messages*.properties`). Not the Spring MVC view engine: the app has no server-rendered pages.
@Configuration(proxyBeanMethods = false)
class MailTemplateConfig {

    @Bean
    MailTemplates mailTemplates() {
        ResourceBundleMessageSource texts = new ResourceBundleMessageSource();
        texts.setBasename("mail/messages");
        texts.setDefaultEncoding(StandardCharsets.UTF_8.name());
        texts.setFallbackToSystemLocale(false);

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.addTemplateResolver(resolver(TemplateMode.HTML, "*.html", 1));
        engine.addTemplateResolver(resolver(TemplateMode.TEXT, "*.txt", 2));
        engine.setTemplateEngineMessageSource(texts);
        return new MailTemplates(engine, texts);
    }

    private static ClassLoaderTemplateResolver resolver(TemplateMode mode, String pattern, int order) {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("mail/");
        resolver.setTemplateMode(mode);
        resolver.setResolvablePatterns(Set.of(pattern));
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setCheckExistence(true);
        resolver.setOrder(order);
        return resolver;
    }
}

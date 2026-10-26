package com.asp.platform.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/// `app.site.privacy-url` is bound and checked at start: an absolute https URL or nothing.
class SitePropertiesTests {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(Config.class);

    @Test
    void should_read_unset_and_blank_as_no_link() {
        context.run(c -> assertThat(c.getBean(SiteProperties.class).privacyUrlOrNull()).isNull());
        context.withPropertyValues("app.site.privacy-url=  ")
                .run(c -> assertThat(c.getBean(SiteProperties.class).privacyUrlOrNull()).isNull());
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://asp.example/privacidade", "https://asp.example",
            "https://asp.example:8443/br/privacidade",
            "http://localhost:4321/privacidade", "http://127.0.0.1/privacidade"})
    void should_accept_an_absolute_https_url_or_a_local_http_one(String url) {
        context.withPropertyValues("app.site.privacy-url= " + url + " ")
                .run(c -> assertThat(c.getBean(SiteProperties.class).privacyUrlOrNull()).isEqualTo(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/privacidade", "asp.example/privacidade", "http://asp.example/privacidade",
            "javascript:alert(1)", "https://asp.example/priv acidade", "https://asp.example/\"onmouseover=x",
            "ftp://asp.example/privacidade"})
    void should_stop_the_start_on_anything_else(String url) {
        context.withPropertyValues("app.site.privacy-url=" + url)
                .run(c -> assertThat(c).getFailure().hasRootCauseInstanceOf(BindValidationException.class));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SiteProperties.class)
    static class Config {
    }
}

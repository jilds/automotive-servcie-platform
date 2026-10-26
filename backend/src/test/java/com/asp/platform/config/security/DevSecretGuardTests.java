package com.asp.platform.config.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class DevSecretGuardTests {

    private static final String RANDOM_SECRET = "a-random-production-secret-of-40-characters";

    @Test
    void should_refuse_the_development_secret_in_production() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");

        assertThatIllegalStateException()
                .isThrownBy(() -> new DevSecretGuard(properties(DevSecretGuard.DEV_SECRET), prod))
                .withMessageContaining("ASP_JWT_SECRET")
                .withMessageNotContaining(DevSecretGuard.DEV_SECRET);
    }

    @Test
    void should_refuse_the_development_secret_in_any_other_profile() {
        MockEnvironment staging = new MockEnvironment();
        staging.setActiveProfiles("staging");

        assertThatIllegalStateException()
                .isThrownBy(() -> new DevSecretGuard(properties(DevSecretGuard.DEV_SECRET), staging));
    }

    @Test
    void should_accept_the_development_secret_in_dev_and_test() {
        MockEnvironment dev = new MockEnvironment();
        dev.setActiveProfiles("dev");
        MockEnvironment test = new MockEnvironment();
        test.setActiveProfiles("test");

        assertThatCode(() -> new DevSecretGuard(properties(DevSecretGuard.DEV_SECRET), dev)).doesNotThrowAnyException();
        assertThatCode(() -> new DevSecretGuard(properties(DevSecretGuard.DEV_SECRET), test)).doesNotThrowAnyException();
    }

    @Test
    void should_refuse_the_development_secret_without_an_active_profile() {
        assertThatIllegalStateException()
                .isThrownBy(() -> new DevSecretGuard(properties(DevSecretGuard.DEV_SECRET), new MockEnvironment()));
    }

    @Test
    void should_guard_the_secret_that_application_dev_yml_really_holds() throws Exception {
        try (InputStream yml = getClass().getResourceAsStream("/application-dev.yml")) {
            assertThat(new String(yml.readAllBytes(), StandardCharsets.UTF_8)).contains(DevSecretGuard.DEV_SECRET);
        }
    }

    @Test
    void should_accept_a_real_secret_in_production() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");

        assertThatCode(() -> new DevSecretGuard(properties(RANDOM_SECRET), prod)).doesNotThrowAnyException();
    }

    private static AppSecurityProperties properties(String secret) {
        return new AppSecurityProperties(secret);
    }
}

package com.asp.platform.billing.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;

class BillingPropertiesTests {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private static BillingProperties with(Duration providerTimeout) {
        return new BillingProperties(new BillingProperties.Invoicing("sandbox"),
                new BillingProperties.Payments("sandbox", "secret"), 100_000, 3, 5,
                new BillingProperties.Retry(5, Duration.ofMinutes(1)), providerTimeout);
    }

    @Test
    void should_keep_the_provider_timeout_well_below_the_row_lock_timeout() {
        assertThat(validator.validate(with(Duration.ofSeconds(2)))).isEmpty();
        assertThat(validator.validate(with(Duration.ofMillis(2500)))).isEmpty();
        assertThat(validator.validate(with(Duration.ofMillis(2501)))).hasSize(1);
        assertThat(validator.validate(with(Duration.ofSeconds(10)))).hasSize(1);
        assertThat(validator.validate(with(Duration.ZERO))).hasSize(1);
    }

    @Test
    void should_not_print_the_webhook_secret() {
        assertThat(with(Duration.ofSeconds(2)).toString()).doesNotContain("secret");
    }
}

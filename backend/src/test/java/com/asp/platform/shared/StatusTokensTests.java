package com.asp.platform.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class StatusTokensTests {

    @Test
    void should_generate_url_safe_tokens_of_43_characters() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String token = StatusTokens.generate();
            assertThat(token).hasSize(StatusTokens.LENGTH).matches("[A-Za-z0-9_-]{43}");
            tokens.add(token);
        }
        assertThat(tokens).as("random, so never repeated").hasSize(200);
    }

    @Test
    void should_hash_with_sha_256_as_lower_case_hex() {
        // SHA-256("abc"), FIPS 180-2 test vector.
        assertThat(StatusTokens.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        String token = StatusTokens.generate();
        assertThat(StatusTokens.hash(token)).hasSize(64).isEqualTo(StatusTokens.hash(token))
                .isNotEqualTo(StatusTokens.hash(StatusTokens.generate()));
    }
}

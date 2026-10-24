package com.asp.platform.config.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/// Rate-limit keys (ADR 0004): IPv4 by address, IPv6 by its /64 prefix.
class ClientAddressesTests {

    @Test
    void should_key_ipv4_by_the_whole_address() {
        assertThat(ClientAddresses.key("203.0.113.7")).isEqualTo("203.0.113.7");
        assertThat(ClientAddresses.key("203.0.113.8")).isNotEqualTo(ClientAddresses.key("203.0.113.7"));
    }

    @Test
    void should_key_ipv6_by_its_64_bit_prefix() {
        String key = ClientAddresses.key("2001:db8:1234:5678:aaaa:bbbb:cccc:dddd");

        assertThat(key).isEqualTo("2001:db8:1234:5678:0:0:0:0/64");
        // Any other address in the same /64, in any spelling, is the same client.
        assertThat(ClientAddresses.key("2001:0db8:1234:5678::1")).isEqualTo(key);
        assertThat(ClientAddresses.key("[2001:db8:1234:5678:ffff::]")).isEqualTo(key);
        assertThat(ClientAddresses.key("2001:db8:1234:5678::1%eth0")).isEqualTo(key);
        // The next /64 is another client.
        assertThat(ClientAddresses.key("2001:db8:1234:5679::1")).isNotEqualTo(key);
    }

    @Test
    void should_key_ipv4_mapped_ipv6_as_ipv4() {
        assertThat(ClientAddresses.key("::ffff:203.0.113.7")).isEqualTo("203.0.113.7");
    }
}

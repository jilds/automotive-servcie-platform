package com.asp.platform.config.prod;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import com.jayway.jsonpath.JsonPath;

/// With `ASP_TRUSTED_PROXIES` naming another address, the loopback peer is not a proxy: its `X-Forwarded-*` headers
/// are ignored, so a client cannot pick its own address and escape the public rate limits.
// Properties syntax: "\\\\" is one backslash in the value, so the regular expression is 10\.9\.9\.9.
@TestPropertySource(properties = "ASP_TRUSTED_PROXIES=10\\\\.9\\\\.9\\\\.9")
class ProdProfileUntrustedProxyTests extends ProdProfileServerTestSupport {

    @Test
    void should_ignore_forwarded_headers_from_an_untrusted_peer() throws Exception {
        HttpResponse<String> probe = get("/test-probe/remote-address",
                Map.of("X-Forwarded-For", "203.0.113.7", "X-Forwarded-Proto", "https"));

        assertThat((String) JsonPath.read(probe.body(), "$.remoteAddr")).isIn("127.0.0.1", "0:0:0:0:0:0:0:1");
        assertThat((Boolean) JsonPath.read(probe.body(), "$.secure")).isFalse();
        assertThat(probe.headers().firstValue("Strict-Transport-Security")).isEmpty();
    }
}

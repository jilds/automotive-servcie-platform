package com.asp.platform.config.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.web.filter.CharacterEncodingFilter;

import com.asp.platform.ApiTestSupport;

/// [AuthLimiter] reads a login body as UTF-8 and refuses other declared charsets. That matches what the controller
/// decodes only while Spring Boot forces UTF-8 for requests (`server.servlet.encoding.force-request`, on by default):
/// fails if somebody turns it off.
class AuthLimiterEncodingTests extends ApiTestSupport {

    @Autowired
    Environment environment;

    @Autowired
    CharacterEncodingFilter encodingFilter;

    @Test
    void should_keep_utf8_request_encoding_forced() {
        assertThat(environment.getProperty("server.servlet.encoding.force-request", Boolean.class, true)).isTrue();
        assertThat(encodingFilter.isForceRequestEncoding()).isTrue();
        assertThat(encodingFilter.getEncoding()).isEqualToIgnoringCase("UTF-8");
    }
}

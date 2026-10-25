package com.asp.platform.config.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/// The public path and slug come from the decoded, normalised servlet path, not the raw request URI.
class PublicPathsTests {

    @Test
    void should_read_the_slug_from_the_servlet_path() {
        String path = "/api/v1/public/oficina-azul/probe";
        MockHttpServletRequest request = request(path, path);

        assertThat(PublicPaths.isPublic(request)).isTrue();
        assertThat(PublicPaths.slug(request)).contains("oficina-azul");
    }

    @Test
    void should_treat_the_public_root_as_public_without_a_slug() {
        for (String path : new String[] {"/api/v1/public", "/api/v1/public/"}) {
            MockHttpServletRequest request = request(path, path);
            assertThat(PublicPaths.isPublic(request)).as(path).isTrue();
            assertThat(PublicPaths.slug(request)).as(path).isEmpty();
        }
    }

    @Test
    void should_use_the_decoded_path_when_the_raw_uri_is_encoded() {
        MockHttpServletRequest request = request("/api/v1/%70ublic/oficina-azul/probe",
                "/api/v1/public/oficina-azul/probe");

        assertThat(PublicPaths.isPublic(request)).isTrue();
        assertThat(PublicPaths.slug(request)).contains("oficina-azul");
    }

    @Test
    void should_join_servlet_path_and_path_info() {
        MockHttpServletRequest request = request("/api/v1/public/oficina-azul/probe", "/api");
        request.setPathInfo("/v1/public/oficina-azul/probe");

        assertThat(PublicPaths.slug(request)).contains("oficina-azul");
    }

    @Test
    void should_not_treat_lookalike_paths_as_public() {
        for (String path : new String[] {"/api/v1/publicity", "/api/v1/customers", "/w/oficina-azul"}) {
            assertThat(PublicPaths.isPublic(request(path, path))).as(path).isFalse();
        }
    }

    private static MockHttpServletRequest request(String requestUri, String servletPath) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", requestUri);
        request.setServletPath(servletPath);
        return request;
    }
}

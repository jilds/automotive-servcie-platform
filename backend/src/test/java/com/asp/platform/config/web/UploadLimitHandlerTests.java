package com.asp.platform.config.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/// A file over the container's multipart limit gets a neutral code from the global handler, for every upload (a
/// logo as well as a photo), and the workshop advice no longer changes it.
class UploadLimitHandlerTests {

    @Test
    void should_answer_413_upload_too_large_for_any_upload_over_the_container_limit() {
        var response = new GlobalExceptionHandler().handleMaxUploadSizeExceededException(
                new MaxUploadSizeExceededException(5L * 1024 * 1024), new HttpHeaders(), HttpStatus.PAYLOAD_TOO_LARGE,
                new ServletWebRequest(new MockHttpServletRequest()));

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode().value()).isEqualTo(413);
        ProblemDetail body = (ProblemDetail) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getProperties()).containsEntry("code", "upload-too-large");
    }

    @Test
    void should_not_let_the_workshop_advice_handle_generic_web_exceptions() throws Exception {
        Class<?> advice = Class.forName("com.asp.platform.workshop.api.WorkshopExceptionHandler");
        assertThat(Arrays.stream(advice.getDeclaredMethods()).flatMap(m -> Arrays.stream(m.getParameterTypes()))
                .map(Class::getSimpleName))
                .doesNotContain("MaxUploadSizeExceededException", "MethodArgumentTypeMismatchException");
    }
}

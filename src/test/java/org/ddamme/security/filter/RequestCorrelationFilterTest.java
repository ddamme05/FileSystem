package org.ddamme.security.filter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RequestCorrelationFilterTest {

    private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

    @Test
    void shouldReturnXRequestIdHeaderWhenNotProvided() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilterInternal(request, response, filterChain);

        String requestId = response.getHeader("X-Request-ID");
        assertThat(requestId).isNotNull().isNotBlank();
        // Should be a UUID format
        assertThat(requestId).matches("[a-f0-9-]{36}");
    }

    @Test
    void shouldEchoProvidedXRequestIdHeader() throws Exception {
        String providedRequestId = "test-request-123";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-ID", providedRequestId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilterInternal(request, response, filterChain);

        String requestId = response.getHeader("X-Request-ID");
        assertThat(requestId).isEqualTo(providedRequestId);
    }

    @Test
    void shouldGenerateNewIdWhenProvidedIdIsBlank() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-ID", "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilterInternal(request, response, filterChain);

        String requestId = response.getHeader("X-Request-ID");
        assertThat(requestId).isNotNull().isNotBlank().doesNotContain("   ");
        // Should be a UUID format, not the blank value we sent
        assertThat(requestId).matches("[a-f0-9-]{36}");
    }
}

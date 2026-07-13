package com.codeforge.ai.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class PublicationViewerIdentityServiceTest {

    @Test
    void loggedInViewerIdentityTest() {
        PublicationViewerIdentityService service = new PublicationViewerIdentityService(
                new JwtProperties("test-issuer", "dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdGVzdGluZy1vbmx5", 3600L));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String viewerKey = service.resolveViewerKey(42L, request, response);

        assertThat(viewerKey).isEqualTo("u:42");
        assertThat(response.getHeader("Set-Cookie")).isNull();
    }

    @Test
    void anonymousViewerCookieTest() {
        PublicationViewerIdentityService service = new PublicationViewerIdentityService(
                new JwtProperties("test-issuer", "dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdGVzdGluZy1vbmx5", 3600L));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String first = service.resolveViewerKey(null, request, response);
        String setCookie = response.getHeader("Set-Cookie");
        assertThat(first).startsWith("a:");
        assertThat(setCookie).contains(PublicationViewerIdentityService.VIEWER_COOKIE_NAME);

        String cookieValue = first.substring("a:".length());
        request.setCookies(new Cookie(PublicationViewerIdentityService.VIEWER_COOKIE_NAME, cookieValue));
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        String second = service.resolveViewerKey(null, request, secondResponse);

        assertThat(second).isEqualTo(first);
        assertThat(secondResponse.getHeader("Set-Cookie")).isNull();
    }

    @Test
    void hashViewerKeyUsesHmac() {
        PublicationViewerIdentityService service = new PublicationViewerIdentityService(
                new JwtProperties("test-issuer", "dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdGVzdGluZy1vbmx5", 3600L));

        String hash = service.hashViewerKey("a:viewer-1");

        assertThat(hash).hasSize(64);
        assertThat(hash).isEqualTo(service.hashViewerKey("a:viewer-1"));
    }
}

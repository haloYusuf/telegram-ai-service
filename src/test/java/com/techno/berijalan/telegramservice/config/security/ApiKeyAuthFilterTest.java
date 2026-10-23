package com.techno.berijalan.telegramservice.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class ApiKeyAuthFilterTest {

    private ApiKeyAuthFilter filter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new ApiKeyAuthFilter();
        filterChain = mock(FilterChain.class);
    }

    @Test
    @DisplayName("CORS OPTIONS request passes without API Key")
    void testCorsOptionsRequestPasses() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/rest/v1/telegram/chat/buckets");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    @DisplayName("Favicon.ico request passes without API Key")
    void testFaviconPasses() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/favicon.ico");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    @DisplayName("Telegram media stream request passes without API Key")
    void testMediaStreamPasses() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/rest/v1/telegram/media/stream");
        request.setParameter("path", "uploads/test.jpg");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    @DisplayName("Telegram webhook request passes without API Key")
    void testWebhookPasses() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/rest/v1/telegram/webhook/bot-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    @DisplayName("Swagger and OpenAPI requests pass without API Key")
    void testSwaggerPasses() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/swagger-ui/index.html");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    @DisplayName("Valid X-API-KEY header passes")
    void testValidApiKeyHeaderPasses() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/rest/v1/telegram/chat/buckets");
        request.addHeader(ApiKeyAuthFilter.API_KEY_HEADER, ApiKeyAuthFilter.EXPECTED_API_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    @DisplayName("Valid apiKey query parameter passes (SSE use case)")
    void testValidApiKeyQueryParamPasses() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/rest/v1/telegram/chat/stream");
        request.setParameter("apiKey", ApiKeyAuthFilter.EXPECTED_API_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    @DisplayName("Missing API Key on protected endpoint returns 401 Unauthorized")
    void testMissingApiKeyReturns401() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/rest/v1/telegram/chat/buckets");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(request, response);
        assertEquals(401, response.getStatus());
    }

    @Test
    @DisplayName("Invalid API Key on protected endpoint returns 401 Unauthorized")
    void testInvalidApiKeyReturns401() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/rest/v1/telegram/chat/send");
        request.addHeader(ApiKeyAuthFilter.API_KEY_HEADER, "WRONG-KEY");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(request, response);
        assertEquals(401, response.getStatus());
    }
}

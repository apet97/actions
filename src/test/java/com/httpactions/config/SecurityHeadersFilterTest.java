package com.httpactions.config;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SecurityHeadersFilterTest {

    private final SecurityHeadersFilter filter = new SecurityHeadersFilter();

    @Test
    void apiPath_setsSecurityHeaders() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/actions");
        request.setRequestURI("/api/actions");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals("max-age=31536000; includeSubDomains; preload", response.getHeader("Strict-Transport-Security"));
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertEquals("DENY", response.getHeader("X-Frame-Options"));
        assertNotNull(response.getHeader("Content-Security-Policy"));
    }

    @Test
    void embeddedUi_skipsFrameOptions() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sidebar");
        request.setRequestURI("/sidebar");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals("max-age=31536000; includeSubDomains; preload", response.getHeader("Strict-Transport-Security"));
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertNull(response.getHeader("X-Frame-Options"));
        assertNull(response.getHeader("Content-Security-Policy"));
    }
}

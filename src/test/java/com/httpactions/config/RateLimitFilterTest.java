package com.httpactions.config;

import com.httpactions.service.TokenService;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    private static final int MAX_REQUESTS = 5;

    @Mock
    private TokenService tokenService;

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter(MAX_REQUESTS, tokenService);
    }

    @Test
    void nonApiPath_passesThrough() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/manifest");
        request.setRequestURI("/manifest");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
        assertEquals(200, response.getStatus());
    }

    @Test
    void sidebarPath_passesThrough() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sidebar");
        request.setRequestURI("/sidebar");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
        assertEquals(200, response.getStatus());
    }

    @Test
    void webhookPath_passesThrough() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/webhook/new-time-entry");
        request.setRequestURI("/webhook/new-time-entry");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
        assertEquals(200, response.getStatus());
    }

    @Test
    void apiPath_withinLimit_passesThrough() throws ServletException, IOException {
        String token = "token-ws-123";
        when(tokenService.verifyAndParseClaims(token)).thenReturn(Map.of("workspaceId", "ws-123"));

        for (int i = 0; i < MAX_REQUESTS; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/actions");
            request.setRequestURI("/api/actions");
            request.addHeader("X-Addon-Token", token);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, response, chain);

            assertNotNull(chain.getRequest(), "Request #" + (i + 1) + " should pass through");
            assertEquals(200, response.getStatus());
        }
    }

    @Test
    void apiPath_exceedingLimit_returns429() throws ServletException, IOException {
        String token = "token-rate-limited";
        when(tokenService.verifyAndParseClaims(token)).thenReturn(Map.of("workspaceId", "ws-rate-limited"));

        for (int i = 0; i < MAX_REQUESTS; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/actions");
            request.setRequestURI("/api/actions");
            request.addHeader("X-Addon-Token", token);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, response, chain);
            assertEquals(200, response.getStatus());
        }

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/actions");
        request.setRequestURI("/api/actions");
        request.addHeader("X-Addon-Token", token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(429, response.getStatus());
        assertEquals("application/json", response.getContentType());
        assertNull(chain.getRequest());
    }

    @Test
    void apiPath_differentWorkspaces_independentLimits() throws ServletException, IOException {
        String tokenA = "token-a";
        String tokenB = "token-b";
        when(tokenService.verifyAndParseClaims(tokenA)).thenReturn(Map.of("workspaceId", "ws-A"));
        when(tokenService.verifyAndParseClaims(tokenB)).thenReturn(Map.of("workspaceId", "ws-B"));

        for (int i = 0; i < MAX_REQUESTS; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/actions");
            request.setRequestURI("/api/actions");
            request.addHeader("X-Addon-Token", tokenA);
            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        }

        MockHttpServletRequest requestB = new MockHttpServletRequest("GET", "/api/actions");
        requestB.setRequestURI("/api/actions");
        requestB.addHeader("X-Addon-Token", tokenB);
        MockHttpServletResponse responseB = new MockHttpServletResponse();
        MockFilterChain chainB = new MockFilterChain();

        filter.doFilter(requestB, responseB, chainB);

        assertNotNull(chainB.getRequest());
        assertEquals(200, responseB.getStatus());
    }

    @Test
    void apiPath_missingToken_passesThrough() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/actions");
        request.setRequestURI("/api/actions");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
        assertEquals(200, response.getStatus());
    }

    @Test
    void apiPath_blankToken_passesThrough() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/actions");
        request.setRequestURI("/api/actions");
        request.addHeader("X-Addon-Token", "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
        assertEquals(200, response.getStatus());
    }

    @Test
    void apiPath_malformedToken_passesThrough() throws ServletException, IOException {
        when(tokenService.verifyAndParseClaims("not-a-jwt")).thenReturn(null);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/actions");
        request.setRequestURI("/api/actions");
        request.addHeader("X-Addon-Token", "not-a-jwt");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
        assertEquals(200, response.getStatus());
    }

    @Test
    void rateLimitFilter_unauthenticatedRequests_rateLimited() throws ServletException, IOException {
        // With MAX_REQUESTS=5, unauthenticated gets max(1, 5/3) = 1 request per second
        int unauthLimit = Math.max(1, MAX_REQUESTS / 3);

        // Fill the unauthenticated IP-based rate limit
        for (int i = 0; i < unauthLimit; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/actions");
            request.setRequestURI("/api/actions");
            request.setRemoteAddr("192.168.1.100");
            // No X-Addon-Token header — unauthenticated
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, response, chain);

            assertNotNull(chain.getRequest(), "Unauthenticated request #" + (i + 1) + " should pass through");
            assertEquals(200, response.getStatus());
        }

        // Next unauthenticated request from the same IP should be rejected
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/actions");
        request.setRequestURI("/api/actions");
        request.setRemoteAddr("192.168.1.100");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(429, response.getStatus());
        assertEquals("application/json", response.getContentType());
        assertNull(chain.getRequest(), "Request exceeding unauthenticated limit should be blocked");
    }
}

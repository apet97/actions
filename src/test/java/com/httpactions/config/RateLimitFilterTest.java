package com.httpactions.config;

import com.httpactions.model.dto.VerifiedAddonContext;
import com.httpactions.service.VerifiedAddonContextService;
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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    private static final int MAX_REQUESTS = 5;

    @Mock
    private TokenService tokenService;

    @Mock
    private VerifiedAddonContextService verifiedAddonContextService;

    private RateLimitFilter filter;
    private AtomicLong nanoClock;

    @BeforeEach
    void setUp() {
        nanoClock = new AtomicLong();
        filter = new RateLimitFilter(MAX_REQUESTS, verifiedAddonContextService, nanoClock::getAndIncrement);
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
        when(verifiedAddonContextService.verifyToken(token)).thenReturn(Optional.of(context("ws-123")));

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
        when(verifiedAddonContextService.verifyToken(token)).thenReturn(Optional.of(context("ws-rate-limited")));

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
        when(verifiedAddonContextService.verifyToken(tokenA)).thenReturn(Optional.of(context("ws-A")));
        when(verifiedAddonContextService.verifyToken(tokenB)).thenReturn(Optional.of(context("ws-B")));

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
    void apiPath_missingToken_firstRequestUnderQuota_passes() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/actions");
        request.setRequestURI("/api/actions");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
        assertEquals(200, response.getStatus());
    }

    @Test
    void apiPath_blankToken_firstRequestUnderQuota_passes() throws ServletException, IOException {
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
    void apiPath_malformedToken_firstRequestUnderQuota_passes() throws ServletException, IOException {
        when(verifiedAddonContextService.verifyToken("not-a-jwt")).thenReturn(Optional.empty());

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

    @Test
    void healthPath_bypassesLimiterAndTokenLookup() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
        request.setRequestURI("/api/health");
        request.addHeader("X-Addon-Token", "ignored");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
        assertEquals(200, response.getStatus());
        verifyNoInteractions(verifiedAddonContextService);
    }

    @Test
    void apiPath_cachedContextAttribute_skipsTokenVerification() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/actions");
        request.setRequestURI("/api/actions");
        request.setAttribute(VerifiedAddonContext.REQUEST_ATTRIBUTE, context("ws-cached"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
        assertEquals(200, response.getStatus());
        verifyNoInteractions(verifiedAddonContextService);
    }

    private VerifiedAddonContext context(String workspaceId) {
        return new VerifiedAddonContext(
                workspaceId,
                "addon-1",
                "https://api.clockify.me/api",
                "en",
                "",
                "DEFAULT",
                Map.of("workspaceId", workspaceId)
        );
    }
}

package com.httpactions.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.httpactions.service.TokenService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Sliding-window rate limiter per workspace. Tracks request timestamps in a deque
 * and rejects requests when the count within the last 1-second window exceeds the limit.
 * Eliminates the 2x burst vulnerability of fixed-window counters at window boundaries.
 */
public class RateLimitFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final int maxRequestsPerSecond;
    private final TokenService tokenService;
    private final LoadingCache<String, ConcurrentLinkedDeque<Long>> requestTimestamps;

    public RateLimitFilter(int maxRequestsPerSecond, TokenService tokenService) {
        this.maxRequestsPerSecond = maxRequestsPerSecond;
        this.tokenService = tokenService;
        this.requestTimestamps = Caffeine.newBuilder()
                .expireAfterAccess(Duration.ofSeconds(10))
                .build(key -> new ConcurrentLinkedDeque<>());
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String path = httpRequest.getRequestURI();

        // Only rate-limit /api/** endpoints
        if (!path.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        Map<String, Object> claims = extractClaimsFromToken(httpRequest);
        String workspaceId = claims != null ? (String) claims.get("workspaceId") : null;
        if (workspaceId == null) {
            // Apply stricter IP-based rate limit for unauthenticated requests
            String clientIp = httpRequest.getRemoteAddr();
            String ipKey = "ip:" + clientIp;
            long now = System.nanoTime();
            long windowStart = now - 1_000_000_000L;
            ConcurrentLinkedDeque<Long> ipTimestamps = requestTimestamps.get(ipKey);
            while (!ipTimestamps.isEmpty() && ipTimestamps.peekFirst() < windowStart) {
                ipTimestamps.pollFirst();
            }
            // Unauthenticated requests get 1/3 the normal rate limit
            if (ipTimestamps.size() >= Math.max(1, maxRequestsPerSecond / 3)) {
                HttpServletResponse httpResponse = (HttpServletResponse) response;
                httpResponse.setStatus(429);
                httpResponse.setContentType("application/json");
                httpResponse.getWriter().write("{\"error\":\"Too many requests\"}");
                return;
            }
            ipTimestamps.addLast(now);
            chain.doFilter(request, response);
            return;
        }
        // Store verified claims as request attribute so controllers don't re-verify JWT
        httpRequest.setAttribute("verifiedClaims", claims);

        long now = System.nanoTime();
        long windowStart = now - 1_000_000_000L; // 1 second ago in nanos

        ConcurrentLinkedDeque<Long> timestamps = requestTimestamps.get(workspaceId);

        // Evict timestamps older than the sliding window
        while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
            timestamps.pollFirst();
        }

        if (timestamps.size() >= maxRequestsPerSecond) {
            log.warn("Rate limit exceeded for workspace {}", workspaceId);
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            httpResponse.setStatus(429);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write("{\"error\":\"Too many requests\"}");
            return;
        }

        timestamps.addLast(now);
        chain.doFilter(request, response);
    }

    private Map<String, Object> extractClaimsFromToken(HttpServletRequest request) {
        String token = request.getHeader("X-Addon-Token");
        if (token == null || token.isBlank()) return null;
        return tokenService.verifyAndParseClaims(token);
    }
}

package com.httpactions.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.httpactions.model.dto.VerifiedAddonContext;
import com.httpactions.service.TokenService;
import com.httpactions.service.VerifiedAddonContextService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.LongSupplier;

/**
 * Sliding-window rate limiter per workspace. Tracks request timestamps in a deque
 * and rejects requests when the count within the last 1-second window exceeds the limit.
 * Eliminates the 2x burst vulnerability of fixed-window counters at window boundaries.
 */
public class RateLimitFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final int UNAUTHENTICATED_DIVISOR = 3;

    private final int maxRequestsPerSecond;
    private final VerifiedAddonContextService verifiedAddonContextService;
    private final LoadingCache<String, ConcurrentLinkedDeque<Long>> requestTimestamps;
    private final LongSupplier nanoTimeSupplier;

    public RateLimitFilter(int maxRequestsPerSecond, VerifiedAddonContextService verifiedAddonContextService) {
        this(maxRequestsPerSecond, verifiedAddonContextService, System::nanoTime);
    }

    public RateLimitFilter(int maxRequestsPerSecond, TokenService tokenService) {
        this(maxRequestsPerSecond, new VerifiedAddonContextService(tokenService), System::nanoTime);
    }

    RateLimitFilter(int maxRequestsPerSecond,
                    VerifiedAddonContextService verifiedAddonContextService,
                    LongSupplier nanoTimeSupplier) {
        this.maxRequestsPerSecond = maxRequestsPerSecond;
        this.verifiedAddonContextService = verifiedAddonContextService;
        this.nanoTimeSupplier = nanoTimeSupplier;
        this.requestTimestamps = Caffeine.newBuilder()
                .expireAfterAccess(Duration.ofSeconds(10))
                .build(key -> new ConcurrentLinkedDeque<>());
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String path = httpRequest.getRequestURI();

        if (!path.startsWith("/api/") || "/api/health".equals(path)) {
            chain.doFilter(request, response);
            return;
        }

        VerifiedAddonContext context = extractContext(httpRequest);
        String group = resolveGroup(path);
        if (context == null || context.workspaceId() == null) {
            String clientIp = httpRequest.getRemoteAddr();
            String key = "ip:" + clientIp + ":" + group;
            int limit = Math.max(1, maxRequestsPerSecond / UNAUTHENTICATED_DIVISOR);
            if (isRateLimited(key, limit)) {
                log.warn("IP rate limit exceeded for group {} from {}", group, clientIp);
                reject((HttpServletResponse) response);
                return;
            }
            chain.doFilter(request, response);
            return;
        }

        String key = context.workspaceId() + ":" + group;
        if (isRateLimited(key, maxRequestsPerSecond)) {
            log.warn("Rate limit exceeded for workspace {} group {}", context.workspaceId(), group);
            reject((HttpServletResponse) response);
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isRateLimited(String key, int limit) {
        long now = nanoTimeSupplier.getAsLong();
        long windowStart = now - 1_000_000_000L;
        ConcurrentLinkedDeque<Long> timestamps = requestTimestamps.get(key);

        while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
            timestamps.pollFirst();
        }

        if (timestamps.size() >= limit) {
            return true;
        }

        timestamps.addLast(now);
        return false;
    }

    private VerifiedAddonContext extractContext(HttpServletRequest request) {
        Object cached = request.getAttribute(VerifiedAddonContext.REQUEST_ATTRIBUTE);
        if (cached instanceof VerifiedAddonContext context) {
            return context;
        }

        String token = request.getHeader("X-Addon-Token");
        if (token == null || token.isBlank()) {
            return null;
        }

        return verifiedAddonContextService.verifyToken(token).orElse(null);
    }

    private String resolveGroup(String path) {
        if (path.startsWith("/api/actions")) {
            return "actions";
        }
        if (path.startsWith("/api/logs")) {
            return "logs";
        }
        if (path.startsWith("/api/events")) {
            return "events";
        }
        if (path.startsWith("/api/templates")) {
            return "templates";
        }
        if (path.startsWith("/api/widget/stats")) {
            return "widget-stats";
        }
        return "other";
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", "1");
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"Too many requests\"}");
    }
}

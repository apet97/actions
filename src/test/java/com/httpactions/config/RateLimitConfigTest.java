package com.httpactions.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitConfigTest {

    @Test
    void requestSizeLimitFilter_includesLifecycleEndpoints() {
        RateLimitConfig config = new RateLimitConfig();

        FilterRegistrationBean<RequestSizeLimitFilter> registration = config.requestSizeLimitFilter();

        assertTrue(registration.getUrlPatterns().contains("/api/*"));
        assertTrue(registration.getUrlPatterns().contains("/webhook/*"));
        assertTrue(registration.getUrlPatterns().contains("/lifecycle/*"));
    }
}

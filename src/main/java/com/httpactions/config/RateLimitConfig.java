package com.httpactions.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimitConfig {

    @Bean
    public FilterRegistrationBean<RequestSizeLimitFilter> requestSizeLimitFilter() {
        FilterRegistrationBean<RequestSizeLimitFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RequestSizeLimitFilter());
        registration.addUrlPatterns("/api/*", "/webhook/*", "/lifecycle/*");
        registration.setOrder(1);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter(
            AddonConfig addonConfig,
            com.httpactions.service.VerifiedAddonContextService verifiedAddonContextService) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RateLimitFilter(
                addonConfig.getOutbound().getRateLimitPerWorkspace(),
                verifiedAddonContextService
        ));
        registration.addUrlPatterns("/api/*");
        registration.setOrder(3);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<AddonClaimsFilter> addonClaimsFilter(
            com.httpactions.service.VerifiedAddonContextService verifiedAddonContextService) {
        FilterRegistrationBean<AddonClaimsFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new AddonClaimsFilter(verifiedAddonContextService));
        registration.addUrlPatterns("/api/*");
        registration.setOrder(2);
        return registration;
    }
}

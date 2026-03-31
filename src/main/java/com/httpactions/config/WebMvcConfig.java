package com.httpactions.config;

import com.httpactions.service.VerifiedAddonContextService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final VerifiedAddonContextService verifiedAddonContextService;

    public WebMvcConfig(VerifiedAddonContextService verifiedAddonContextService) {
        this.verifiedAddonContextService = verifiedAddonContextService;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new VerifiedAddonContextArgumentResolver(verifiedAddonContextService));
    }
}

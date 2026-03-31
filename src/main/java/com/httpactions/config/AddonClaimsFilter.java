package com.httpactions.config;

import com.httpactions.model.dto.VerifiedAddonContext;
import com.httpactions.service.VerifiedAddonContextService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class AddonClaimsFilter extends OncePerRequestFilter {

    private final VerifiedAddonContextService verifiedAddonContextService;

    public AddonClaimsFilter(VerifiedAddonContextService verifiedAddonContextService) {
        this.verifiedAddonContextService = verifiedAddonContextService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if ("/api/health".equals(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = request.getHeader("X-Addon-Token");
        if (token != null && !token.isBlank()) {
            verifiedAddonContextService.verifyToken(token)
                    .ifPresent(context -> request.setAttribute(VerifiedAddonContext.REQUEST_ATTRIBUTE, context));
        }

        filterChain.doFilter(request, response);
    }
}

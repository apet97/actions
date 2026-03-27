package com.httpactions.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload");
        response.setHeader("X-Content-Type-Options", "nosniff");

        String path = request.getRequestURI();
        boolean isIframePath = "/sidebar".equals(path) || "/widget".equals(path);

        if (!isIframePath) {
            response.setHeader("X-Frame-Options", "DENY");
            // Restrictive CSP for non-iframe paths (API, manifest, static assets)
            response.setHeader("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
        }
        // Iframe paths get their CSP from SidebarController/WidgetController with frame-ancestors *.clockify.me

        filterChain.doFilter(request, response);
    }
}

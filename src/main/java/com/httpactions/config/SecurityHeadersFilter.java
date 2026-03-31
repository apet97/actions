package com.httpactions.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    private static final String PERMISSIONS_POLICY =
            "accelerometer=(), camera=(), geolocation=(), microphone=(), payment=(), usb=()";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Permissions-Policy", PERMISSIONS_POLICY);

        String path = request.getRequestURI();
        boolean isIframePath = "/sidebar".equals(path) || "/widget".equals(path);
        boolean isStaticAsset = path.startsWith("/css/")
                || path.startsWith("/js/")
                || path.startsWith("/images/")
                || path.matches(".*\\.(png|svg|ico|jpg|jpeg|gif|webp|woff|woff2|ttf|map)$");

        if (!isIframePath && !isStaticAsset) {
            response.setHeader("X-Frame-Options", "DENY");
            response.setHeader("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
        }

        filterChain.doFilter(request, response);
    }
}

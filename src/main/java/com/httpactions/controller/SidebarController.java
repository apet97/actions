package com.httpactions.controller;

import com.httpactions.service.TokenService;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@Controller
public class SidebarController {

    private static final Logger log = LoggerFactory.getLogger(SidebarController.class);
    private static final String CSP_HEADER = "default-src 'self'; " +
            "script-src 'self' 'unsafe-inline' https://resources.developer.clockify.me; " +
            "style-src 'self' 'unsafe-inline' https://resources.developer.clockify.me; " +
            "img-src 'self' data: https://resources.developer.clockify.me; " +
            "connect-src 'self'; frame-ancestors https://*.clockify.me";

    private final TokenService tokenService;

    public SidebarController(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @GetMapping("/sidebar")
    public String sidebar(@RequestParam(name = "auth_token", required = false) String authToken,
                          Model model, HttpServletResponse response) {
        // C10: Set CSP header
        response.setHeader("Content-Security-Policy", CSP_HEADER);

        String theme = "DEFAULT";
        String workspaceId = "";
        String language = "en";
        String timezone = "";

        if (authToken != null) {
            // C5 fix: verify JWT signature instead of just decoding
            Map<String, Object> claims = tokenService.verifyAndParseClaims(authToken);
            if (claims != null) {
                Object themeObj = claims.get("theme");
                if (themeObj instanceof String t) {
                    theme = t;
                }
                Object wsObj = claims.get("workspaceId");
                if (wsObj instanceof String ws) {
                    workspaceId = ws;
                }
                Object languageObj = claims.get("language");
                if (languageObj instanceof String lang && !lang.isBlank()) {
                    language = lang;
                }
                Object timezoneObj = claims.get("timezone");
                if (timezoneObj instanceof String zone && !zone.isBlank()) {
                    timezone = zone;
                }
            } else {
                log.warn("Sidebar: invalid or expired auth token");
            }
        }

        model.addAttribute("theme", theme);
        model.addAttribute("isDark", "DARK".equals(theme));
        model.addAttribute("workspaceId", workspaceId);
        model.addAttribute("language", language);
        model.addAttribute("timezone", timezone);
        return "sidebar";
    }
}

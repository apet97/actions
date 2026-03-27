package com.httpactions.controller;

import com.httpactions.model.dto.WidgetStats;
import com.httpactions.service.LogService;
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
public class WidgetController {

    private static final Logger log = LoggerFactory.getLogger(WidgetController.class);
    private static final String CSP_HEADER = "default-src 'self'; " +
            "script-src 'self' 'unsafe-inline' https://resources.developer.clockify.me; " +
            "style-src 'self' 'unsafe-inline' https://resources.developer.clockify.me; " +
            "img-src 'self' data: https://resources.developer.clockify.me; " +
            "connect-src 'self'; frame-ancestors https://*.clockify.me";

    private final TokenService tokenService;
    private final LogService logService;

    public WidgetController(TokenService tokenService, LogService logService) {
        this.tokenService = tokenService;
        this.logService = logService;
    }

    @GetMapping("/widget")
    public String widget(@RequestParam(name = "auth_token", required = false) String authToken,
                         Model model, HttpServletResponse response) {
        // C10: Set CSP header
        response.setHeader("Content-Security-Policy", CSP_HEADER);

        String theme = "DEFAULT";
        String workspaceId = "";
        String language = "en";
        String timezone = "";

        if (authToken != null) {
            // C6 fix: verify JWT signature instead of just decoding
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
                log.warn("Widget: invalid or expired auth token");
            }
        }

        // Fetch aggregate stats
        WidgetStats stats;
        if (!workspaceId.isEmpty()) {
            stats = logService.getWidgetStats(workspaceId);
        } else {
            // L5: use 0% instead of misleading 100% for missing workspaces
            stats = new WidgetStats(0, 0, 0, 0.0);
        }

        model.addAttribute("theme", theme);
        model.addAttribute("isDark", "DARK".equals(theme));
        model.addAttribute("workspaceId", workspaceId);
        model.addAttribute("stats", stats);

        // Determine success rate badge color
        double rate = stats.getSuccessRate24h();
        String rateClass;
        String rateBadgePrefix;
        if (rate >= 95.0) {
            rateClass = "green";
            rateBadgePrefix = "OK";
        } else if (rate >= 80.0) {
            rateClass = "yellow";
            rateBadgePrefix = "WARN";
        } else {
            rateClass = "red";
            rateBadgePrefix = "ALERT";
        }
        model.addAttribute("rateClass", rateClass);
        model.addAttribute("rateBadgePrefix", rateBadgePrefix);
        model.addAttribute("language", language);
        model.addAttribute("timezone", timezone);

        return "widget";
    }
}

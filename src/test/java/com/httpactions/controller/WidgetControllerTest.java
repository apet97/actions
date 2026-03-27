package com.httpactions.controller;

import com.httpactions.model.dto.WidgetStats;
import com.httpactions.service.LogService;
import com.httpactions.service.TokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = WidgetController.class,
    excludeAutoConfiguration = SecurityAutoConfiguration.class
)
@AutoConfigureMockMvc(addFilters = false)
class WidgetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TokenService tokenService;

    @MockitoBean
    private LogService logService;

    private static final String VALID_TOKEN = "valid-widget-jwt";

    // ── GET /widget without auth_token ──

    @Test
    @DisplayName("GET /widget without auth_token returns widget view")
    void widget_noToken_returnsWidgetView() throws Exception {
        mockMvc.perform(get("/widget"))
                .andExpect(status().isOk())
                .andExpect(view().name("widget"))
                .andExpect(model().attribute("theme", "DEFAULT"))
                .andExpect(model().attribute("isDark", false))
                .andExpect(model().attribute("workspaceId", ""))
                .andExpect(model().attribute("language", "en"))
                .andExpect(model().attribute("timezone", ""))
                .andExpect(model().attributeExists("stats"))
                .andExpect(model().attribute("rateClass", "red"))
                .andExpect(model().attribute("rateBadgePrefix", "ALERT"));
    }

    // ── GET /widget with valid token ──

    @Test
    @DisplayName("GET /widget with valid token returns widget with stats")
    void widget_validToken_returnsWidgetWithStats() throws Exception {
        Map<String, Object> claims = new HashMap<>();
        claims.put("workspaceId", "ws-widget-1");
        claims.put("theme", "DARK");
        claims.put("language", "en-GB");
        claims.put("timezone", "Europe/London");
        claims.put("iss", "clockify");
        claims.put("type", "addon");

        when(tokenService.verifyAndParseClaims(VALID_TOKEN)).thenReturn(claims);

        WidgetStats stats = new WidgetStats(5, 100, 3, 97.0);
        when(logService.getWidgetStats("ws-widget-1")).thenReturn(stats);

        mockMvc.perform(get("/widget").param("auth_token", VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(view().name("widget"))
                .andExpect(model().attribute("theme", "DARK"))
                .andExpect(model().attribute("isDark", true))
                .andExpect(model().attribute("workspaceId", "ws-widget-1"))
                .andExpect(model().attribute("language", "en-GB"))
                .andExpect(model().attribute("timezone", "Europe/London"))
                .andExpect(model().attribute("stats", stats))
                .andExpect(model().attribute("rateClass", "green"))
                .andExpect(model().attribute("rateBadgePrefix", "OK"));
    }

    // ── CSP header ──

    @Test
    @DisplayName("CSP header is present in response (C10)")
    void widget_cspHeaderPresent() throws Exception {
        mockMvc.perform(get("/widget"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().string("Content-Security-Policy",
                        org.hamcrest.Matchers.containsString("frame-ancestors")));
    }
}

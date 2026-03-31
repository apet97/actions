package com.httpactions.controller;

import com.httpactions.model.dto.IframeViewContext;
import com.httpactions.model.dto.WidgetStats;
import com.httpactions.service.IframeViewSupport;
import com.httpactions.service.LogService;
import com.httpactions.service.VerifiedAddonContextService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
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
    private IframeViewSupport iframeViewSupport;

    @MockitoBean
    private LogService logService;

    @MockitoBean
    private VerifiedAddonContextService verifiedAddonContextService;

    private static final String VALID_TOKEN = "valid-widget-jwt";

    // ── GET /widget without auth_token ──

    @Test
    @DisplayName("GET /widget without auth_token returns widget view")
    void widget_noToken_returnsWidgetView() throws Exception {
        when(iframeViewSupport.buildContext(isNull(), any(), eq("Widget")))
                .thenReturn(new IframeViewContext("DEFAULT", false, "", "en", ""));
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
        when(iframeViewSupport.buildContext(eq(VALID_TOKEN), any(), eq("Widget")))
                .thenReturn(new IframeViewContext("DARK", true, "ws-widget-1", "en-GB", "Europe/London"));

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
        doAnswer(invocation -> {
            jakarta.servlet.http.HttpServletResponse response = invocation.getArgument(1);
            response.setHeader("Content-Security-Policy", "default-src 'self'; frame-ancestors https://*.clockify.me");
            response.setHeader("Referrer-Policy", "no-referrer");
            return new IframeViewContext("DEFAULT", false, "", "en", "");
        }).when(iframeViewSupport).buildContext(isNull(), any(), eq("Widget"));

        mockMvc.perform(get("/widget"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().string("Content-Security-Policy",
                        org.hamcrest.Matchers.containsString("frame-ancestors")));
    }
}

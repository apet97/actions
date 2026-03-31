package com.httpactions.controller;

import com.httpactions.model.dto.IframeViewContext;
import com.httpactions.service.IframeViewSupport;
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
    controllers = SidebarController.class,
    excludeAutoConfiguration = SecurityAutoConfiguration.class
)
@AutoConfigureMockMvc(addFilters = false)
class SidebarControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IframeViewSupport iframeViewSupport;

    @MockitoBean
    private VerifiedAddonContextService verifiedAddonContextService;

    private static final String VALID_TOKEN = "valid-sidebar-jwt";

    // ── GET /sidebar without auth_token ──

    @Test
    @DisplayName("GET /sidebar without auth_token returns sidebar view")
    void sidebar_noToken_returnsSidebarView() throws Exception {
        when(iframeViewSupport.buildContext(isNull(), any(), eq("Sidebar")))
                .thenReturn(new IframeViewContext("DEFAULT", false, "", "en", ""));

        mockMvc.perform(get("/sidebar"))
                .andExpect(status().isOk())
                .andExpect(view().name("sidebar"))
                .andExpect(model().attribute("theme", "DEFAULT"))
                .andExpect(model().attribute("isDark", false))
                .andExpect(model().attribute("workspaceId", ""))
                .andExpect(model().attribute("language", "en"))
                .andExpect(model().attribute("timezone", ""));
    }

    // ── GET /sidebar with valid token ──

    @Test
    @DisplayName("GET /sidebar with valid token returns sidebar with theme")
    void sidebar_validToken_returnsSidebarWithTheme() throws Exception {
        when(iframeViewSupport.buildContext(eq(VALID_TOKEN), any(), eq("Sidebar")))
                .thenReturn(new IframeViewContext("DARK", true, "ws-sidebar-1", "en", "Europe/Belgrade"));

        mockMvc.perform(get("/sidebar").param("auth_token", VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(view().name("sidebar"))
                .andExpect(model().attribute("theme", "DARK"))
                .andExpect(model().attribute("isDark", true))
                .andExpect(model().attribute("workspaceId", "ws-sidebar-1"))
                .andExpect(model().attribute("language", "en"))
                .andExpect(model().attribute("timezone", "Europe/Belgrade"));
    }

    // ── CSP header ──

    @Test
    @DisplayName("CSP header is present in response (C10)")
    void sidebar_cspHeaderPresent() throws Exception {
        doAnswer(invocation -> {
            jakarta.servlet.http.HttpServletResponse response = invocation.getArgument(1);
            response.setHeader("Content-Security-Policy", "default-src 'self'; frame-ancestors https://*.clockify.me");
            response.setHeader("Referrer-Policy", "no-referrer");
            return new IframeViewContext("DEFAULT", false, "", "en", "");
        }).when(iframeViewSupport).buildContext(isNull(), any(), eq("Sidebar"));

        mockMvc.perform(get("/sidebar"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().string("Content-Security-Policy",
                        org.hamcrest.Matchers.containsString("frame-ancestors")));
    }
}

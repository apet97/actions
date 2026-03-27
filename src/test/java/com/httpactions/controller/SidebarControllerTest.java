package com.httpactions.controller;

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
    controllers = SidebarController.class,
    excludeAutoConfiguration = SecurityAutoConfiguration.class
)
@AutoConfigureMockMvc(addFilters = false)
class SidebarControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TokenService tokenService;

    private static final String VALID_TOKEN = "valid-sidebar-jwt";

    // ── GET /sidebar without auth_token ──

    @Test
    @DisplayName("GET /sidebar without auth_token returns sidebar view")
    void sidebar_noToken_returnsSidebarView() throws Exception {
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
        Map<String, Object> claims = new HashMap<>();
        claims.put("workspaceId", "ws-sidebar-1");
        claims.put("theme", "DARK");
        claims.put("language", "sr");
        claims.put("timezone", "Europe/Belgrade");
        claims.put("iss", "clockify");
        claims.put("type", "addon");

        when(tokenService.verifyAndParseClaims(VALID_TOKEN)).thenReturn(claims);

        mockMvc.perform(get("/sidebar").param("auth_token", VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(view().name("sidebar"))
                .andExpect(model().attribute("theme", "DARK"))
                .andExpect(model().attribute("isDark", true))
                .andExpect(model().attribute("workspaceId", "ws-sidebar-1"))
                .andExpect(model().attribute("language", "sr"))
                .andExpect(model().attribute("timezone", "Europe/Belgrade"));
    }

    // ── CSP header ──

    @Test
    @DisplayName("CSP header is present in response (C10)")
    void sidebar_cspHeaderPresent() throws Exception {
        mockMvc.perform(get("/sidebar"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().string("Content-Security-Policy",
                        org.hamcrest.Matchers.containsString("frame-ancestors")));
    }
}

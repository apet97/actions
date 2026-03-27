package com.httpactions.controller;

import com.httpactions.config.AddonConfig;
import com.httpactions.model.enums.EventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = ManifestController.class,
    excludeAutoConfiguration = SecurityAutoConfiguration.class
)
@AutoConfigureMockMvc(addFilters = false)
class ManifestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AddonConfig addonConfig;

    @Test
    @DisplayName("GET /manifest returns valid JSON")
    void getManifest_returnsValidJson() throws Exception {
        when(addonConfig.getKey()).thenReturn("http-actions-test");
        when(addonConfig.getBaseUrl()).thenReturn("https://my-addon.example.com");

        mockMvc.perform(get("/manifest"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$").isMap());
    }

    @Test
    @DisplayName("Manifest contains required fields (key, name, baseUrl)")
    void getManifest_containsRequiredFields() throws Exception {
        when(addonConfig.getKey()).thenReturn("http-actions-test");
        when(addonConfig.getBaseUrl()).thenReturn("https://my-addon.example.com");

        mockMvc.perform(get("/manifest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key", is("http-actions-test")))
                .andExpect(jsonPath("$.name", is("HTTP Actions")))
                .andExpect(jsonPath("$.baseUrl", is("https://my-addon.example.com")))
                .andExpect(jsonPath("$.schemaVersion", is("1.3")))
                .andExpect(jsonPath("$.minimalSubscriptionPlan", is("FREE")))
                .andExpect(jsonPath("$.scopes", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.lifecycle", hasSize(4)))
                .andExpect(jsonPath("$.webhooks", hasSize(10)))
                .andExpect(jsonPath("$.components", hasSize(2)));
    }

    @Test
    @DisplayName("Manifest exposes only the scopes used by the addon")
    void getManifest_usesLeastPrivilegeScopes() throws Exception {
        when(addonConfig.getKey()).thenReturn("http-actions-test");
        when(addonConfig.getBaseUrl()).thenReturn("https://my-addon.example.com");

        mockMvc.perform(get("/manifest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scopes", hasSize(7)))
                .andExpect(jsonPath("$.scopes", not(hasItem("EXPENSE_READ"))))
                .andExpect(jsonPath("$.scopes", not(hasItem("APPROVAL_READ"))))
                .andExpect(jsonPath("$.scopes", not(hasItem("GROUP_READ"))));
    }

    @Test
    @DisplayName("Manifest keeps USER_JOINED_WORKSPACE path aligned with EventType slug")
    void getManifest_userJoinedWebhookPathMatchesEnumSlug() throws Exception {
        when(addonConfig.getKey()).thenReturn("http-actions-test");
        when(addonConfig.getBaseUrl()).thenReturn("https://my-addon.example.com");

        mockMvc.perform(get("/manifest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.webhooks[?(@.event == 'USER_JOINED_WORKSPACE')].path",
                        contains("/webhook/" + EventType.USER_JOINED_WORKSPACE.getSlug())
                ));
    }
}

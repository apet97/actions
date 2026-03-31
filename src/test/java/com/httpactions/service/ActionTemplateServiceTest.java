package com.httpactions.service;

import com.httpactions.service.ActionTemplateService.ActionTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ActionTemplateServiceTest {

    private ActionTemplateService service;

    @BeforeEach
    void setUp() {
        service = new ActionTemplateService();
    }

    // --- getTemplates ---

    @Test
    void getTemplates_returnsExternalTemplatesOnly() {
        List<ActionTemplate> templates = service.getTemplates();
        // Clockify Automation templates removed — now only external templates remain
        assertEquals(8, templates.size());
    }

    @Test
    void getTemplates_returnsUnmodifiableList() {
        List<ActionTemplate> templates = service.getTemplates();
        assertThrows(UnsupportedOperationException.class, () -> templates.add(
                new ActionTemplate("x", "x", "x", "x", "x", "POST", "http://x", null, null)));
    }

    @Test
    void getTemplates_containsExpectedIds() {
        Set<String> ids = service.getTemplates().stream()
                .map(ActionTemplate::getId)
                .collect(Collectors.toSet());

        assertTrue(ids.contains("slack-webhook"));
        assertTrue(ids.contains("discord-webhook"));
        assertTrue(ids.contains("generic-rest-post"));
        assertTrue(ids.contains("generic-rest-get"));
        assertTrue(ids.contains("google-sheets-append"));
        assertTrue(ids.contains("project-created-webhook"));
        assertTrue(ids.contains("invoice-created-slack"));
        assertTrue(ids.contains("user-joined-webhook"));
    }

    @Test
    void templates_haveDiverseEventTypes() {
        Set<String> eventTypes = service.getTemplates().stream()
                .map(ActionTemplate::getEventType)
                .collect(Collectors.toSet());

        assertTrue(eventTypes.size() >= 4,
                "Expected at least 4 distinct event types, got: " + eventTypes);
        assertTrue(eventTypes.contains("NEW_TIME_ENTRY"));
        assertTrue(eventTypes.contains("NEW_PROJECT"));
        assertTrue(eventTypes.contains("NEW_INVOICE"));
        assertTrue(eventTypes.contains("USER_JOINED_WORKSPACE"));
    }

    @Test
    void templates_allHaveRequiredFields() {
        for (ActionTemplate t : service.getTemplates()) {
            assertNotNull(t.getId(), "id must not be null");
            assertNotNull(t.getName(), "name must not be null");
            assertNotNull(t.getDescription(), "description must not be null");
            assertNotNull(t.getCategory(), "category must not be null");
            assertNotNull(t.getEventType(), "eventType must not be null");
            assertNotNull(t.getHttpMethod(), "httpMethod must not be null");
            assertNotNull(t.getUrlTemplate(), "urlTemplate must not be null");
            assertNotNull(t.getSampleHeaders(), "sampleHeaders must not be null");
        }
    }
}

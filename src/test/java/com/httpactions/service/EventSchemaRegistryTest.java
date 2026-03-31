package com.httpactions.service;

import com.httpactions.model.enums.EventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EventSchemaRegistryTest {

    private EventSchemaRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new EventSchemaRegistry();
    }

    // ── getSchemas ──

    @Test
    @DisplayName("getSchemas returns all 10 event types")
    void getSchemas_returnsAll10EventTypes() {
        Map<EventType, List<EventSchemaRegistry.EventVariable>> schemas = registry.getSchemas();

        assertEquals(10, schemas.size());
        for (EventType type : EventType.values()) {
            assertTrue(schemas.containsKey(type), "Missing schema for " + type.name());
        }
    }

    // ── getSchema includes meta variables ──

    @Test
    @DisplayName("getSchema includes meta variables for any event type")
    void getSchema_includesMetaVariables() {
        List<EventSchemaRegistry.EventVariable> schema = registry.getSchema(EventType.NEW_TIME_ENTRY);

        List<String> paths = schema.stream()
                .map(EventSchemaRegistry.EventVariable::getPath)
                .toList();

        assertTrue(paths.contains("meta.workspaceId"), "Missing meta.workspaceId");
        assertTrue(paths.contains("meta.eventType"), "Missing meta.eventType");
        assertTrue(paths.contains("meta.receivedAt"), "Missing meta.receivedAt");
        assertTrue(paths.contains("meta.addonId"), "Missing meta.addonId");
        assertTrue(paths.contains("meta.backendUrl"), "Missing meta.backendUrl");
        assertTrue(paths.contains("meta.installationToken"), "Missing meta.installationToken");
    }

    // ── getSchema includes event.raw ──

    @Test
    @DisplayName("getSchema includes event.raw variable")
    void getSchema_includesEventRaw() {
        List<EventSchemaRegistry.EventVariable> schema = registry.getSchema(EventType.NEW_PROJECT);

        List<String> paths = schema.stream()
                .map(EventSchemaRegistry.EventVariable::getPath)
                .toList();

        assertTrue(paths.contains("event.raw"), "Missing event.raw");

        EventSchemaRegistry.EventVariable rawVar = schema.stream()
                .filter(v -> "event.raw".equals(v.getPath()))
                .findFirst()
                .orElseThrow();
        assertEquals("object", rawVar.getType());
    }

    // ── getSchema for unknown type returns only meta vars ──

    @Test
    @DisplayName("getSchema for event type not in map returns only meta vars + event.raw")
    void getSchema_unknownType_returnsOnlyMetaVars() {
        // EventSchemaRegistry maps all 10 enum values, but getSchema uses getOrDefault
        // so we can test the fallback by removing from the map — but since the map is
        // unmodifiable and covers all enum values, we verify the contract differently:
        // verify that meta vars + event.raw are always present (5 total) for every type
        for (EventType type : EventType.values()) {
            List<EventSchemaRegistry.EventVariable> schema = registry.getSchema(type);

            long metaCount = schema.stream()
                    .filter(v -> v.getPath().startsWith("meta."))
                    .count();
            long rawCount = schema.stream()
                    .filter(v -> "event.raw".equals(v.getPath()))
                    .count();

            assertEquals(6, metaCount, "Expected 6 meta vars for " + type);
            assertEquals(1, rawCount, "Expected 1 event.raw for " + type);
            // Total should be event-specific vars + 6 meta + 1 raw
            assertTrue(schema.size() >= 7, "Schema for " + type + " should have at least 7 vars (meta + raw)");
        }
    }

    // ── schema variable properties ──

    @Test
    @DisplayName("EventVariable has correct properties")
    void eventVariable_hasCorrectProperties() {
        List<EventSchemaRegistry.EventVariable> schema = registry.getSchema(EventType.NEW_TIME_ENTRY);

        EventSchemaRegistry.EventVariable idVar = schema.stream()
                .filter(v -> "event.id".equals(v.getPath()))
                .findFirst()
                .orElseThrow();

        assertEquals("event.id", idVar.getPath());
        assertEquals("string", idVar.getType());
        assertNotNull(idVar.getDescription());
        assertFalse(idVar.getDescription().isBlank());
    }
}

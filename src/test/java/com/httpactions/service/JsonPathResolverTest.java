package com.httpactions.service;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JsonPathResolverTest {

    // ── resolve top-level field ──

    @Test
    @DisplayName("resolve top-level field returns correct value")
    void resolve_topLevelField() {
        JsonObject root = JsonParser.parseString("""
                {"id": "te-123", "description": "Test entry", "billable": true}
                """).getAsJsonObject();

        assertEquals("te-123", JsonPathResolver.resolveAsString(root, "id"));
        assertEquals("Test entry", JsonPathResolver.resolveAsString(root, "description"));
        assertEquals("true", JsonPathResolver.resolveAsString(root, "billable"));
    }

    // ── resolve nested field (dot notation) ──

    @Test
    @DisplayName("resolve nested field via dot notation")
    void resolve_nestedField() {
        JsonObject root = JsonParser.parseString("""
                {"timeInterval": {"start": "2024-01-01T00:00:00Z", "duration": "PT1H30M"}}
                """).getAsJsonObject();

        assertEquals("2024-01-01T00:00:00Z", JsonPathResolver.resolveAsString(root, "timeInterval.start"));
        assertEquals("PT1H30M", JsonPathResolver.resolveAsString(root, "timeInterval.duration"));
    }

    // ── resolve array access ──

    @Test
    @DisplayName("resolve array access returns indexed element")
    void resolve_arrayAccess() {
        JsonObject root = JsonParser.parseString("""
                {"tags": [{"id": "tag-1", "name": "Urgent"}, {"id": "tag-2", "name": "Billable"}]}
                """).getAsJsonObject();

        assertEquals("tag-1", JsonPathResolver.resolveAsString(root, "tags[0].id"));
        assertEquals("Urgent", JsonPathResolver.resolveAsString(root, "tags[0].name"));
        assertEquals("tag-2", JsonPathResolver.resolveAsString(root, "tags[1].id"));
        assertEquals("Billable", JsonPathResolver.resolveAsString(root, "tags[1].name"));
    }

    // ── resolve missing field returns empty string ──

    @Test
    @DisplayName("resolve missing field returns empty string")
    void resolve_missingField_returnsEmpty() {
        JsonObject root = JsonParser.parseString("""
                {"id": "te-123"}
                """).getAsJsonObject();

        assertEquals("", JsonPathResolver.resolveAsString(root, "nonexistent"));
        assertEquals("", JsonPathResolver.resolveAsString(root, "deep.nested.path"));
        assertEquals("", JsonPathResolver.resolveAsString(root, "tags[0].name"));
    }

    // ── resolve null element returns empty string ──

    @Test
    @DisplayName("resolve null element returns empty string")
    void resolve_nullElement_returnsEmpty() {
        JsonObject root = JsonParser.parseString("""
                {"projectId": null, "nested": {"value": null}}
                """).getAsJsonObject();

        assertEquals("", JsonPathResolver.resolveAsString(root, "projectId"));
        assertEquals("", JsonPathResolver.resolveAsString(root, "nested.value"));
    }

    // ── resolve with null/blank path ──

    @Test
    @DisplayName("resolve with null root or blank path returns empty/null")
    void resolve_nullInputs() {
        JsonObject root = JsonParser.parseString("{\"id\": \"te-123\"}").getAsJsonObject();

        assertNull(JsonPathResolver.resolve(null, "id"));
        assertNull(JsonPathResolver.resolve(root, null));
        assertNull(JsonPathResolver.resolve(root, ""));
        assertNull(JsonPathResolver.resolve(root, "   "));
    }

    // ── resolve returns raw JsonElement for objects/arrays ──

    @Test
    @DisplayName("resolve returns JSON string for object/array values")
    void resolve_objectAndArray_returnsJsonString() {
        JsonObject root = JsonParser.parseString("""
                {"timeInterval": {"start": "2024-01-01", "end": "2024-01-02"}, "tags": ["a", "b"]}
                """).getAsJsonObject();

        String intervalStr = JsonPathResolver.resolveAsString(root, "timeInterval");
        assertTrue(intervalStr.contains("start"));
        assertTrue(intervalStr.contains("end"));

        String tagsStr = JsonPathResolver.resolveAsString(root, "tags");
        assertTrue(tagsStr.contains("a"));
        assertTrue(tagsStr.contains("b"));
    }

    // ── resolve array out of bounds ──

    @Test
    @DisplayName("resolve array with out-of-bounds index returns empty")
    void resolve_arrayOutOfBounds_returnsEmpty() {
        JsonObject root = JsonParser.parseString("""
                {"tags": [{"id": "tag-1"}]}
                """).getAsJsonObject();

        assertEquals("", JsonPathResolver.resolveAsString(root, "tags[5].id"));
    }
}

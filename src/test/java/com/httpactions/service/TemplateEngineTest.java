package com.httpactions.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TemplateEngineTest {

    private TemplateEngine engine;

    @BeforeEach
    void setUp() {
        engine = new TemplateEngine();
    }

    // --- Simple variable resolution ---

    @Test
    void interpolate_simpleVariable_replacedCorrectly() {
        JsonObject event = new JsonObject();
        event.addProperty("id", "abc");

        String result = engine.interpolate("{{event.id}}", event, Map.of(), false);
        assertEquals("abc", result);
    }

    @Test
    void interpolate_nestedVariable_resolvedViaDotNotation() {
        JsonObject user = new JsonObject();
        user.addProperty("name", "Alice");
        JsonObject event = new JsonObject();
        event.add("user", user);

        String result = engine.interpolate("{{event.user.name}}", event, Map.of(), false);
        assertEquals("Alice", result);
    }

    @Test
    void interpolate_arrayAccess_resolvedByIndex() {
        JsonObject tag0 = new JsonObject();
        tag0.addProperty("name", "urgent");
        JsonArray tags = new JsonArray();
        tags.add(tag0);
        JsonObject event = new JsonObject();
        event.add("tags", tags);

        String result = engine.interpolate("{{event.tags[0].name}}", event, Map.of(), false);
        assertEquals("urgent", result);
    }

    @Test
    void interpolate_missingVariable_replacedWithEmptyString() {
        JsonObject event = new JsonObject();
        event.addProperty("id", "abc");

        String result = engine.interpolate("prefix-{{event.missing}}-suffix", event, Map.of(), false);
        assertEquals("prefix--suffix", result);
    }

    // --- Meta variables ---

    @Test
    void interpolate_metaVariables_resolvedFromMeta() {
        JsonObject event = new JsonObject();
        Map<String, String> meta = Map.of("workspaceId", "ws-123", "eventType", "TIME_ENTRY_CREATED");

        String result = engine.interpolate("ws={{meta.workspaceId}}", event, meta, false);
        assertEquals("ws=ws-123", result);
    }

    // --- event.raw ---

    @Test
    void interpolate_eventRaw_returnsEntirePayload() {
        JsonObject event = new JsonObject();
        event.addProperty("id", "abc");
        event.addProperty("name", "test");

        String result = engine.interpolate("{{event.raw}}", event, Map.of(), false);
        assertEquals(event.toString(), result);
    }

    // --- URL encoding ---

    @Test
    void interpolate_urlEncode_true_encodesValues() {
        JsonObject event = new JsonObject();
        event.addProperty("name", "hello world");

        String result = engine.interpolate("{{event.name}}", event, Map.of(), true);
        // URLEncoder.encode with UTF-8 encodes spaces as '+'
        assertTrue(result.contains("+") || result.contains("%20"),
                "Expected URL-encoded value, got: " + result);
        assertFalse(result.contains(" "), "Space should be encoded");
    }

    @Test
    void interpolate_urlEncode_false_noEncoding() {
        JsonObject event = new JsonObject();
        event.addProperty("name", "hello world");

        String result = engine.interpolate("{{event.name}}", event, Map.of(), false);
        assertEquals("hello world", result);
    }

    // --- prev.* variables ---

    @Test
    void interpolate_prevVars_resolvedFromPreviousResult() {
        JsonObject event = new JsonObject();
        Map<String, String> prevVars = new HashMap<>();
        prevVars.put("prev.status", "200");
        prevVars.put("prev.body", "{\"ok\":true}");

        String result = engine.interpolate(
                "status={{prev.status}}", event, Map.of(), false, prevVars);
        assertEquals("status=200", result);
    }

    @Test
    void interpolate_prevVars_null_prevVariablesIgnored() {
        JsonObject event = new JsonObject();

        String result = engine.interpolate(
                "status={{prev.status}}", event, Map.of(), false, null);
        assertEquals("status=", result);
    }

    // --- Null / edge cases ---

    @Test
    void interpolate_nullTemplate_returnsNull() {
        assertNull(engine.interpolate(null, new JsonObject(), Map.of(), false));
    }

    @Test
    void interpolate_noVariables_returnsTemplateUnchanged() {
        String template = "https://example.com/api/v1/resource";
        String result = engine.interpolate(template, new JsonObject(), Map.of(), false);
        assertEquals(template, result);
    }

    // --- Multiple variables ---

    @Test
    void interpolate_multipleVariables_allReplaced() {
        JsonObject event = new JsonObject();
        event.addProperty("id", "te-99");
        event.addProperty("projectId", "proj-42");

        Map<String, String> meta = Map.of("workspaceId", "ws-7");

        String template = "https://api.example.com/ws/{{meta.workspaceId}}/entries/{{event.id}}?project={{event.projectId}}";
        String result = engine.interpolate(template, event, meta, false);

        assertEquals("https://api.example.com/ws/ws-7/entries/te-99?project=proj-42", result);
    }
}

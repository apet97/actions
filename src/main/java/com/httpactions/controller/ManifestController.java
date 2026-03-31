package com.httpactions.controller;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.httpactions.config.AddonConfig;
import com.httpactions.config.GsonProvider;
import com.httpactions.model.enums.EventType;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ManifestController {

    private final AddonConfig addonConfig;
    private final Gson gson = GsonProvider.get();

    public ManifestController(AddonConfig addonConfig) {
        this.addonConfig = addonConfig;
    }

    @GetMapping(value = "/manifest", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getManifest() {
        JsonObject manifest = new JsonObject();
        manifest.addProperty("schemaVersion", "1.3");
        manifest.addProperty("key", addonConfig.getKey());
        manifest.addProperty("name", "HTTP Actions");
        manifest.addProperty("baseUrl", addonConfig.getBaseUrl());
        manifest.addProperty("description", "Turn Clockify events into HTTP requests. Postman meets webhooks.");
        manifest.addProperty("iconPath", "/icon.png");
        manifest.addProperty("minimalSubscriptionPlan", "FREE");

        // Scopes
        JsonArray scopes = new JsonArray();
        for (String scope : new String[]{
                "TIME_ENTRY_READ", "TIME_ENTRY_WRITE",
                "PROJECT_READ", "PROJECT_WRITE",
                "TASK_READ", "TASK_WRITE",
                "CLIENT_READ", "CLIENT_WRITE",
                "TAG_READ", "TAG_WRITE",
                "USER_READ", "GROUP_WRITE",
                "INVOICE_READ", "INVOICE_WRITE",
                "CUSTOM_FIELDS_READ", "CUSTOM_FIELDS_WRITE"
        }) {
            scopes.add(scope);
        }
        manifest.add("scopes", scopes);

        // Lifecycle events
        JsonArray lifecycle = new JsonArray();
        lifecycle.add(lifecycleEntry("/lifecycle/installed", "INSTALLED"));
        lifecycle.add(lifecycleEntry("/lifecycle/deleted", "DELETED"));
        lifecycle.add(lifecycleEntry("/lifecycle/status-changed", "STATUS_CHANGED"));
        lifecycle.add(lifecycleEntry("/lifecycle/settings-updated", "SETTINGS_UPDATED"));
        manifest.add("lifecycle", lifecycle);

        // Webhooks (10 fixed events)
        JsonArray webhooks = new JsonArray();
        for (EventType eventType : EventType.values()) {
            JsonObject wh = new JsonObject();
            wh.addProperty("event", eventType.name());
            wh.addProperty("path", "/webhook/" + eventType.getSlug());
            webhooks.add(wh);
        }
        manifest.add("webhooks", webhooks);

        // Components
        JsonArray components = new JsonArray();
        JsonObject sidebar = new JsonObject();
        sidebar.addProperty("type", "sidebar");
        sidebar.addProperty("label", "HTTP Actions");
        sidebar.addProperty("path", "/sidebar");
        sidebar.addProperty("accessLevel", "ADMINS");
        sidebar.addProperty("iconPath", "/sidebar-icon.png");
        components.add(sidebar);

        JsonObject widget = new JsonObject();
        widget.addProperty("type", "widget");
        widget.addProperty("label", "HTTP Actions");
        widget.addProperty("path", "/widget");
        widget.addProperty("accessLevel", "ADMINS");
        components.add(widget);

        manifest.add("components", components);

        // Strip empty arrays (SDK gotcha #1)
        String json = gson.toJson(stripEmptyArrays(manifest));
        return ResponseEntity.ok(json);
    }

    private JsonObject lifecycleEntry(String path, String type) {
        JsonObject entry = new JsonObject();
        entry.addProperty("path", path);
        entry.addProperty("type", type);
        return entry;
    }

    /**
     * Recursively strip empty arrays from JSON to avoid Clockify manifest rejection.
     */
    private JsonElement stripEmptyArrays(JsonElement element) {
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            JsonObject result = new JsonObject();
            for (var entry : obj.entrySet()) {
                JsonElement value = entry.getValue();
                if (value.isJsonArray() && value.getAsJsonArray().isEmpty()) {
                    continue; // skip empty arrays
                }
                result.add(entry.getKey(), stripEmptyArrays(value));
            }
            return result;
        } else if (element.isJsonArray()) {
            JsonArray arr = element.getAsJsonArray();
            JsonArray result = new JsonArray();
            for (JsonElement item : arr) {
                result.add(stripEmptyArrays(item));
            }
            return result;
        }
        return element;
    }
}

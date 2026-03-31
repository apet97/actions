package com.httpactions.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared utility for resolving dot-notation paths in Gson {@link JsonObject} trees.
 *
 * <p>Supports nested object traversal and array index access (e.g. {@code tags[0].name}).</p>
 */
public final class JsonPathResolver {

    private static final Pattern ARRAY_ACCESS_PATTERN = Pattern.compile("(.+?)\\[(\\d+)]");

    private JsonPathResolver() {} // utility class

    /**
     * Resolve a dot-notation path in a JsonObject, returning the raw JsonElement.
     * Returns {@code null} if the path doesn't exist or leads to a JSON null.
     *
     * @param root the root JSON object to traverse
     * @param path dot-notation path, e.g. {@code "timeInterval.duration"} or {@code "tags[0].name"}
     * @return the resolved element, or {@code null} if not found / JSON null
     */
    public static JsonElement resolve(JsonObject root, String path) {
        if (root == null || path == null || path.isBlank()) return null;

        String[] segments = path.split("\\.");
        JsonElement current = root;

        for (String segment : segments) {
            if (current == null || current.isJsonNull()) return null;

            Matcher arrayMatcher = ARRAY_ACCESS_PATTERN.matcher(segment);
            if (arrayMatcher.matches()) {
                String field = arrayMatcher.group(1);
                int index = Integer.parseInt(arrayMatcher.group(2));

                if (current.isJsonObject()) {
                    current = current.getAsJsonObject().get(field);
                } else {
                    return null;
                }

                if (current != null && current.isJsonArray()) {
                    JsonArray arr = current.getAsJsonArray();
                    if (index >= 0 && index < arr.size()) {
                        current = arr.get(index);
                    } else {
                        return null;
                    }
                } else {
                    return null;
                }
            } else {
                if (current.isJsonObject()) {
                    current = current.getAsJsonObject().get(segment);
                } else {
                    return null;
                }
            }
        }

        if (current == null || current.isJsonNull()) return null;
        return current;
    }

    /**
     * Resolve a path and convert to string. Primitives return their value;
     * objects/arrays return their JSON representation. Null or missing returns empty string.
     *
     * @param root the root JSON object to traverse
     * @param path dot-notation path
     * @return the resolved string value, or {@code ""} if not found
     */
    public static String resolveAsString(JsonObject root, String path) {
        JsonElement element = resolve(root, path);
        if (element == null) return "";

        if (element.isJsonPrimitive()) {
            JsonPrimitive prim = element.getAsJsonPrimitive();
            if (prim.isString()) return prim.getAsString();
            return prim.toString();
        }

        return element.toString();
    }
}

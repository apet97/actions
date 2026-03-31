package com.httpactions.service;

import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class VariableInterpolator {

    private static final Logger log = LoggerFactory.getLogger(VariableInterpolator.class);
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(.+?)\\}\\}");
    private static final int MAX_URL_SIZE = 8192;
    private static final int MAX_BODY_SIZE = 1_048_576; // 1 MB

    /**
     * Interpolate template variables in the given template string.
     *
     * @param template Template with {{variable}} placeholders
     * @param event    Webhook event payload as JsonObject
     * @param meta     Metadata map (workspaceId, eventType, receivedAt, addonId, backendUrl, installationToken)
     * @param urlEncode Whether to URL-encode resolved values (for URL templates)
     * @return Interpolated string
     */
    public String interpolate(String template, JsonObject event, Map<String, String> meta, boolean urlEncode) {
        return interpolate(template, event, meta, urlEncode, null);
    }

    /**
     * Interpolate template variables with optional previous-action result variables.
     * <p>
     * When {@code urlEncode} is true, only variables in the query string portion (after '?')
     * are URL-encoded. Variables in the scheme/host/path are interpolated verbatim so that
     * {{meta.backendUrl}} resolves to a valid URL prefix.
     *
     * @param template  Template with {{variable}} placeholders
     * @param event     Webhook event payload as JsonObject
     * @param meta      Metadata map
     * @param urlEncode Whether to URL-encode resolved values in the query string
     * @param prevVars  Variables from a previous chained action result (nullable).
     * @return Interpolated string
     */
    public String interpolate(String template, JsonObject event, Map<String, String> meta,
                              boolean urlEncode, Map<String, String> prevVars) {
        if (template == null || template.isBlank()) return template;

        String result;
        if (!urlEncode) {
            result = doInterpolate(template, event, meta, prevVars, false);
        } else {
            // Split at first '?' to encode only query string values
            int qIdx = template.indexOf('?');
            if (qIdx < 0) {
                // No query string — interpolate path without encoding
                result = doInterpolate(template, event, meta, prevVars, false);
            } else {
                String pathPart = doInterpolate(template.substring(0, qIdx), event, meta, prevVars, false);
                String queryPart = doInterpolate(template.substring(qIdx), event, meta, prevVars, true);
                result = pathPart + queryPart;
            }
        }

        // Enforce size limits after expansion
        int maxSize = urlEncode ? MAX_URL_SIZE : MAX_BODY_SIZE;
        int sizeBytes = result == null ? 0 : result.getBytes(StandardCharsets.UTF_8).length;
        if (result != null && sizeBytes > maxSize) {
            String kind = urlEncode ? "URL" : "body";
            log.warn("Interpolated {} exceeds max size ({} > {}), rejecting", kind, sizeBytes, maxSize);
            throw new IllegalStateException("Interpolated " + kind
                    + " exceeds maximum allowed size of " + maxSize + " bytes");
        }

        return result;
    }

    private String doInterpolate(String template, JsonObject event, Map<String, String> meta,
                                 Map<String, String> prevVars, boolean encode) {
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String path = matcher.group(1).trim();
            String resolved = resolve(path, event, meta, prevVars);
            if (encode) {
                resolved = URLEncoder.encode(resolved, StandardCharsets.UTF_8);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(resolved));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    private String resolve(String path, JsonObject event, Map<String, String> meta,
                           Map<String, String> prevVars) {
        // prev.* variables — from previous chained action result
        if (path.startsWith("prev.")) {
            if (prevVars == null) return "";
            // Direct lookup first (prev.status, prev.body)
            String value = prevVars.get(path);
            if (value != null) return value;
            // For prev.headers.X-Something, try exact key
            return prevVars.getOrDefault(path, "");
        }

        // meta.* variables
        if (path.startsWith("meta.")) {
            String metaKey = path.substring(5);
            return meta.getOrDefault(metaKey, "");
        }

        // event.raw — entire payload as JSON string
        if ("event.raw".equals(path)) {
            return event != null ? event.toString() : "";
        }

        // event.* variables — traverse JSON
        if (path.startsWith("event.")) {
            String jsonPath = path.substring(6);
            return resolveJsonPath(event, jsonPath);
        }

        // Unrecognized prefix — treat as event path for backwards compatibility
        return resolveJsonPath(event, path);
    }

    private String resolveJsonPath(JsonObject root, String path) {
        return JsonPathResolver.resolveAsString(root, path);
    }
}

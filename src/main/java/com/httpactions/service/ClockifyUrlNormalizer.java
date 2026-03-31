package com.httpactions.service;

import java.net.URI;
import java.net.URISyntaxException;

public final class ClockifyUrlNormalizer {

    private ClockifyUrlNormalizer() {}

    public static String normalizeBackendApiUrl(String rawUrl) {
        return normalize(rawUrl, false);
    }

    public static String normalizeBackendApiV1Url(String rawUrl) {
        return normalize(rawUrl, true);
    }

    private static String normalize(String rawUrl, boolean appendV1) {
        URI uri = URI.create(rawUrl);
        String path = uri.getPath() == null ? "" : uri.getPath().replaceAll("/+$", "");

        if (path.isBlank() || "/".equals(path)) {
            path = "/api";
        } else if (path.matches(".*/api/v\\d+$")) {
            path = path.replaceAll("/api/v\\d+$", "/api");
        } else if (!path.endsWith("/api")) {
            path = path + "/api";
        }

        if (appendV1 && !path.endsWith("/api/v1")) {
            path = path + "/v1";
        }

        try {
            return new URI(
                    uri.getScheme(),
                    uri.getUserInfo(),
                    uri.getHost(),
                    uri.getPort(),
                    path,
                    uri.getQuery(),
                    uri.getFragment()
            ).toString();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid Clockify backend URL: " + rawUrl, e);
        }
    }
}

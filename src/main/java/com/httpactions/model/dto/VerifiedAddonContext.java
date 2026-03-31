package com.httpactions.model.dto;

import java.util.Map;

public record VerifiedAddonContext(
        String workspaceId,
        String addonId,
        String backendUrl,
        String language,
        String timezone,
        String theme,
        Map<String, Object> claims
) {
    public static final String REQUEST_ATTRIBUTE = VerifiedAddonContext.class.getName();
}

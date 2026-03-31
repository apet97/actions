package com.httpactions.model.dto;

public record IframeViewContext(
        String theme,
        boolean dark,
        String workspaceId,
        String language,
        String timezone
) {
}

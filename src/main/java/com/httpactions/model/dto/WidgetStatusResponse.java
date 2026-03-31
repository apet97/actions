package com.httpactions.model.dto;

public record WidgetStatusResponse(
        WidgetStats stats,
        String rateClass,
        String rateBadgePrefix
) {
}

package com.httpactions.model.dto;

import java.util.List;

public record EventTypeResponse(
        String name,
        String slug,
        String label,
        List<EventVariableResponse> variables
) {
}

package com.httpactions.model.dto;

public record EventVariableResponse(
        String path,
        String type,
        String description
) {
}

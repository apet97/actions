package com.httpactions.model.dto;

import java.util.List;

public record ActionImportResponse(
        List<ActionResponse> created,
        List<String> skipped,
        String warning
) {
}

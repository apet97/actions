package com.httpactions.service;

import com.httpactions.model.dto.VerifiedAddonContext;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class VerifiedAddonContextService {

    private final TokenService tokenService;

    public VerifiedAddonContextService(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    public Optional<VerifiedAddonContext> verifyToken(String token) {
        Map<String, Object> claims = tokenService.verifyAndParseClaims(token);
        if (claims == null) {
            return Optional.empty();
        }
        return Optional.of(fromClaims(claims));
    }

    public VerifiedAddonContext fromClaims(Map<String, Object> claims) {
        Map<String, Object> copy = new LinkedHashMap<>(claims);
        String backendUrl = copy.get("backendUrl") instanceof String url && !url.isBlank()
                ? ClockifyUrlNormalizer.normalizeBackendApiUrl(url)
                : null;

        return new VerifiedAddonContext(
                asString(copy.get("workspaceId")),
                asString(copy.get("addonId")),
                backendUrl,
                defaultString(asString(copy.get("language")), "en"),
                defaultString(asString(copy.get("timezone")), ""),
                defaultString(asString(copy.get("theme")), "DEFAULT"),
                Map.copyOf(copy)
        );
    }

    private static String asString(Object value) {
        return value instanceof String s ? s : null;
    }

    private static String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}

package com.httpactions.service;

import com.httpactions.config.AddonConfig;
import com.httpactions.config.GsonProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import java.util.Map;

@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);

    private final PublicKey clockifyPublicKey;
    private final TextEncryptor textEncryptor;
    private final AddonConfig addonConfig;

    public TokenService(PublicKey clockifyPublicKey, TextEncryptor textEncryptor, AddonConfig addonConfig) {
        this.clockifyPublicKey = clockifyPublicKey;
        this.textEncryptor = textEncryptor;
        this.addonConfig = addonConfig;
    }

    /**
     * Verify a Clockify-signed JWT (lifecycle, webhook, or user token).
     * Fail-closed: any error results in null (rejected).
     */
    public Map<String, Object> verifyAndParseClaims(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length != 3) {
                log.warn("JWT does not have 3 parts");
                return null;
            }

            // Verify alg header is RS256
            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]));
            Map<String, Object> header = GsonProvider.get().fromJson(headerJson, Map.class);
            String alg = header != null ? (String) header.get("alg") : null;
            if (!"RS256".equals(alg)) {
                log.warn("JWT algorithm is not RS256: {}", alg);
                return null;
            }

            // Verify RSA256 signature
            byte[] signatureBytes = Base64.getUrlDecoder().decode(parts[2]);
            byte[] signedContent = (parts[0] + "." + parts[1]).getBytes();

            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(clockifyPublicKey);
            sig.update(signedContent);
            if (!sig.verify(signatureBytes)) {
                log.warn("JWT signature verification failed");
                return null;
            }

            // Decode payload
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
            @SuppressWarnings("unchecked")
            Map<String, Object> claims = GsonProvider.get().fromJson(payloadJson, Map.class);

            // Validate standard claims
            String iss = (String) claims.get("iss");
            String type = (String) claims.get("type");
            String sub = (String) claims.get("sub");

            if (!"clockify".equals(iss)) {
                log.warn("JWT issuer is not 'clockify': {}", iss);
                return null;
            }
            if (!"addon".equals(type)) {
                log.warn("JWT type is not 'addon': {}", type);
                return null;
            }
            if (!addonConfig.getKey().equals(sub)) {
                log.warn("JWT subject does not match addon key: {}", sub);
                return null;
            }

            // Check expiration if present
            if (claims.containsKey("exp")) {
                double exp = ((Number) claims.get("exp")).doubleValue();
                if (System.currentTimeMillis() / 1000.0 > exp) {
                    log.warn("JWT has expired");
                    return null;
                }
            }

            // Normalize claim aliases
            return normalizeClaims(claims);

        } catch (Exception e) {
            log.error("JWT verification failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Normalize JWT claim aliases (apiUrl -> backendUrl, activeWs -> workspaceId).
     */
    private Map<String, Object> normalizeClaims(Map<String, Object> claims) {
        // workspaceId: prefer canonical, fall back to activeWs
        if (!claims.containsKey("workspaceId") && claims.containsKey("activeWs")) {
            claims.put("workspaceId", claims.get("activeWs"));
        }

        // backendUrl: prefer canonical, fall back to apiUrl/baseURL/baseUrl
        if (!claims.containsKey("backendUrl")) {
            Object backendUrl = claims.getOrDefault("apiUrl",
                    claims.getOrDefault("baseURL", claims.get("baseUrl")));
            if (backendUrl != null) {
                claims.put("backendUrl", backendUrl);
            }
        }

        // Normalize backendUrl pathname to end with /api
        Object backendUrlObj = claims.get("backendUrl");
        if (backendUrlObj instanceof String backendUrl) {
            try {
                URI uri = URI.create(backendUrl);
                String path = uri.getPath().replaceAll("/+$", "");
                if (path.isEmpty() || "/".equals(path)) {
                    path = "/api";
                } else if (!path.endsWith("/api")) {
                    // Strip /v1 or similar suffixes
                    if (path.matches(".*/api/v\\d+.*")) {
                        path = path.replaceAll("/api/v\\d+.*", "/api");
                    } else {
                        path = path + "/api";
                    }
                }
                claims.put("backendUrl", uri.getScheme() + "://" + uri.getHost()
                        + (uri.getPort() > 0 ? ":" + uri.getPort() : "") + path);
            } catch (Exception e) {
                log.warn("Failed to normalize backendUrl: {}", backendUrl);
            }
        }

        return claims;
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) return null;
        return textEncryptor.encrypt(plaintext);
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) return null;
        return textEncryptor.decrypt(ciphertext);
    }
}

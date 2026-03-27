package com.httpactions.service;

import com.httpactions.config.AddonConfig;
import com.httpactions.model.dto.TestResult;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

@Service
public class HttpActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(HttpActionExecutor.class);
    private static final int MAX_RESPONSE_BODY_SIZE = 4096;

    private final OutboundRestClientFactory outboundRestClientFactory;
    private final AddonConfig addonConfig;

    public HttpActionExecutor(OutboundRestClientFactory outboundRestClientFactory, AddonConfig addonConfig) {
        this.outboundRestClientFactory = outboundRestClientFactory;
        this.addonConfig = addonConfig;
    }

    /**
     * Execute an outbound HTTP request and return the result.
     */
    public TestResult execute(String method, String url, Map<String, String> headers, String body,
                              String signingSecret) {
        TestResult result = new TestResult();
        long startTime = System.currentTimeMillis();

        try {
            // SSRF protection: validate URL before connecting
            boolean allowHttp = addonConfig.getOutbound().isAllowHttp();
            ValidatedUrl validatedUrl = UrlValidator.validateUrl(url, allowHttp);

            try (CloseableHttpClient client = outboundRestClientFactory.createPinnedClient(validatedUrl)) {
                RestClient restClient = outboundRestClientFactory.create(client);

                // Build request
                RestClient.RequestBodySpec spec = restClient.method(
                        org.springframework.http.HttpMethod.valueOf(method))
                        .uri(validatedUrl.uri());

                // Apply headers
                if (headers != null) {
                    headers.forEach(spec::header);
                }

                // M11: set default Content-Type if body present and no Content-Type header set
                if (body != null && !body.isBlank()) {
                    boolean hasContentType = headers != null && headers.keySet().stream()
                            .anyMatch(k -> k.equalsIgnoreCase("Content-Type"));
                    if (!hasContentType) {
                        spec.header("Content-Type", "application/json");
                    }
                }

                // Apply HMAC signing if configured
                if (signingSecret != null && !signingSecret.isBlank()) {
                    String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
                    String signedPayload = method + "\n" + url + "\n" + timestamp + "\n" + (body != null ? body : "");
                    String signature = computeHmacSha256(signedPayload, signingSecret);
                    spec.header("X-HTTP-Actions-Signature", "sha256=" + signature);
                    spec.header("X-HTTP-Actions-Timestamp", timestamp);
                }

                // Set body
                if (body != null && !body.isBlank()) {
                    spec.body(body);
                }

                // Execute — no-op status handler lets ALL responses (including 4xx/5xx) flow through,
                // so we always capture headers and body for success conditions and prev.* chain variables
                ResponseEntity<String> response = spec.retrieve()
                        .onStatus(HttpStatusCode::isError, (req, res) -> { /* no-op: handle below */ })
                        .toEntity(String.class);

                long elapsed = System.currentTimeMillis() - startTime;

                result.setResponseStatus(response.getStatusCode().value());
                result.setResponseTimeMs((int) elapsed);
                result.setSuccess(response.getStatusCode().is2xxSuccessful());

                // Truncate response body
                String responseBody = response.getBody();
                if (responseBody != null && responseBody.length() > MAX_RESPONSE_BODY_SIZE) {
                    responseBody = responseBody.substring(0, MAX_RESPONSE_BODY_SIZE) + "...[truncated]";
                }
                result.setResponseBody(responseBody);

                // Extract response headers (available for ALL status codes now)
                Map<String, String> responseHeaders = new HashMap<>();
                response.getHeaders().forEach((key, values) -> {
                    if (!values.isEmpty()) {
                        responseHeaders.put(key, values.getFirst());
                    }
                });
                result.setResponseHeaders(responseHeaders);

                if (!response.getStatusCode().is2xxSuccessful()) {
                    log.debug("HTTP request to {} returned status {}", url, response.getStatusCode().value());
                }
            }

        } catch (RestClientException e) {
            long elapsed = System.currentTimeMillis() - startTime;
            result.setResponseTimeMs((int) elapsed);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            log.debug("HTTP request to {} failed: {}", url, e.getMessage());
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            result.setResponseTimeMs((int) elapsed);
            result.setSuccess(false);
            result.setErrorMessage("Unexpected error: " + e.getMessage());
            log.warn("Unexpected error executing HTTP request to {}: {}", url, e.getMessage());
        }

        return result;
    }

    private String computeHmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute HMAC-SHA256", e);
        }
    }
}

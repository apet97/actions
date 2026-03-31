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
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

@Service
public class HttpActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(HttpActionExecutor.class);
    static final int MAX_RESPONSE_BODY_SIZE = 4096;
    private static final int MAX_RESPONSE_HEADERS = 50;
    private static final int MAX_RESPONSE_HEADERS_TOTAL_SIZE = 8192;

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
        try (PreparedRequest preparedRequest = prepare(url)) {
            return execute(preparedRequest, method, url, headers, body, signingSecret);
        } catch (Exception e) {
            TestResult result = new TestResult();
            result.setSuccess(false);
            result.setErrorMessage(e instanceof RestClientException
                    ? e.getMessage()
                    : "Unexpected error: " + e.getMessage());
            log.debug("HTTP request to {} failed before execution: {}", url, e.getMessage());
            return result;
        }
    }

    public PreparedRequest prepare(String url) throws Exception {
        boolean allowHttp = addonConfig.getOutbound().isAllowHttp();
        ValidatedUrl validatedUrl = UrlValidator.validateUrl(url, allowHttp);
        CloseableHttpClient client = outboundRestClientFactory.createPinnedClient(validatedUrl);
        try {
            RestClient restClient = outboundRestClientFactory.create(client);
            return new PreparedRequest(validatedUrl, client, restClient);
        } catch (Exception e) {
            client.close();
            throw e;
        }
    }

    public TestResult execute(PreparedRequest preparedRequest, String method, String url,
                              Map<String, String> headers, String body, String signingSecret) {
        TestResult result = new TestResult();
        long startTime = System.nanoTime();

        try {
            RestClient.RequestBodySpec spec = preparedRequest.restClient()
                    .method(org.springframework.http.HttpMethod.valueOf(method))
                    .uri(preparedRequest.validatedUrl().uri());

            if (headers != null) {
                headers.forEach(spec::header);
            }

            boolean hasContentType = headers != null && headers.keySet().stream()
                    .anyMatch(k -> k.equalsIgnoreCase("Content-Type"));
            boolean hasUserAgent = headers != null && headers.keySet().stream()
                    .anyMatch(k -> k.equalsIgnoreCase("User-Agent"));

            if (!hasUserAgent) {
                spec.header("User-Agent", addonConfig.getKey() + "/1.0");
            }
            if (body != null && !body.isBlank() && !hasContentType) {
                spec.header("Content-Type", "application/json");
            }

            if (signingSecret != null && !signingSecret.isBlank()) {
                String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
                String signedPayload = method + "\n" + url + "\n" + timestamp + "\n" + (body != null ? body : "");
                String signature = computeHmacSha256(signedPayload, signingSecret);
                spec.header("X-HTTP-Actions-Signature", "sha256=" + signature);
                spec.header("X-HTTP-Actions-Timestamp", timestamp);
            }

            if (body != null && !body.isBlank()) {
                spec.body(body);
            }

            ResponseEntity<String> response = spec.retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> { })
                    .toEntity(String.class);

            long elapsed = (System.nanoTime() - startTime) / 1_000_000L;

            result.setResponseStatus(response.getStatusCode().value());
            result.setResponseTimeMs((int) Math.min(elapsed, Integer.MAX_VALUE));
            result.setSuccess(response.getStatusCode().is2xxSuccessful());
            result.setResponseBody(response.getBody());

            Map<String, String> responseHeaders = new HashMap<>();
            int totalSize = 0;
            for (Map.Entry<String, java.util.List<String>> entry : response.getHeaders().entrySet()) {
                if (responseHeaders.size() >= MAX_RESPONSE_HEADERS) {
                    log.debug("Response header count capped at {}", MAX_RESPONSE_HEADERS);
                    break;
                }
                if (!entry.getValue().isEmpty()) {
                    String value = entry.getValue().getFirst();
                    totalSize += entry.getKey().length() + (value != null ? value.length() : 0);
                    if (totalSize > MAX_RESPONSE_HEADERS_TOTAL_SIZE) {
                        log.debug("Response header total size capped at {} bytes", MAX_RESPONSE_HEADERS_TOTAL_SIZE);
                        break;
                    }
                    responseHeaders.put(entry.getKey(), value);
                }
            }
            result.setResponseHeaders(responseHeaders);

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.debug("HTTP request to {} returned status {}", url, response.getStatusCode().value());
            }
        } catch (RestClientException e) {
            long elapsed = (System.nanoTime() - startTime) / 1_000_000L;
            result.setResponseTimeMs((int) Math.min(elapsed, Integer.MAX_VALUE));
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            log.debug("HTTP request to {} failed: {}", url, e.getMessage());
        } catch (Exception e) {
            long elapsed = (System.nanoTime() - startTime) / 1_000_000L;
            result.setResponseTimeMs((int) Math.min(elapsed, Integer.MAX_VALUE));
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
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Failed to compute HMAC-SHA256", e);
        }
    }

    public static final class PreparedRequest implements AutoCloseable {
        private final ValidatedUrl validatedUrl;
        private final CloseableHttpClient client;
        private final RestClient restClient;

        PreparedRequest(ValidatedUrl validatedUrl, CloseableHttpClient client, RestClient restClient) {
            this.validatedUrl = validatedUrl;
            this.client = client;
            this.restClient = restClient;
        }

        ValidatedUrl validatedUrl() {
            return validatedUrl;
        }

        RestClient restClient() {
            return restClient;
        }

        @Override
        public void close() throws Exception {
            client.close();
        }
    }
}

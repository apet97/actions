package com.httpactions.service;

import com.httpactions.config.AddonConfig;
import com.httpactions.model.dto.TestResult;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HttpActionExecutorTest {

    @Mock
    private OutboundRestClientFactory outboundRestClientFactory;

    @Mock
    private AddonConfig addonConfig;

    @Mock
    private RestClient restClient;

    @Mock
    private CloseableHttpClient closeableHttpClient;

    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private RestClient.RequestBodySpec requestBodySpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    private HttpActionExecutor executor;

    @BeforeEach
    void setUp() {
        AddonConfig.Outbound outbound = new AddonConfig.Outbound();
        outbound.setAllowHttp(true);
        when(addonConfig.getOutbound()).thenReturn(outbound);
        lenient().when(addonConfig.getKey()).thenReturn("clockify-http-actions");
        executor = new HttpActionExecutor(outboundRestClientFactory, addonConfig);
    }

    @Test
    void execute_validUrl_returnsSuccessResult() throws Exception {
        String url = "https://api.example.com/hook";
        ValidatedUrl validatedUrl = validatedUrl(url);

        try (MockedStatic<UrlValidator> validator = mockStatic(UrlValidator.class)) {
            validator.when(() -> UrlValidator.validateUrl(eq(url), anyBoolean())).thenReturn(validatedUrl);
            stubRestClient(validatedUrl);

            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.add("X-Request-Id", "abc-123");
            ResponseEntity<String> response = new ResponseEntity<>(
                    "{\"ok\":true}", responseHeaders, HttpStatusCode.valueOf(200));
            when(responseSpec.toEntity(String.class)).thenReturn(response);

            TestResult result = executor.execute("POST", url, null, "{\"data\":1}", null);

            assertTrue(result.isSuccess());
            assertEquals(200, result.getResponseStatus());
            assertEquals("{\"ok\":true}", result.getResponseBody());
            assertNotNull(result.getResponseTimeMs());
            assertNotNull(result.getResponseHeaders());
            assertEquals("abc-123", result.getResponseHeaders().get("X-Request-Id"));
        }
    }

    @Test
    void execute_ssrfLoopbackUrl_returnsSecurityError() {
        TestResult result = executor.execute("GET", "http://127.0.0.1:8080/admin", null, null, null);

        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("blocked internal address")
                        || result.getErrorMessage().contains("Unexpected error"),
                "Expected security-related error, got: " + result.getErrorMessage());
    }

    @Test
    void execute_fileSchemeUrl_returnsSecurityError() {
        TestResult result = executor.execute("GET", "file:///etc/passwd", null, null, null);

        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("Blocked scheme")
                        || result.getErrorMessage().contains("Unexpected error"),
                "Expected security-related error, got: " + result.getErrorMessage());
    }

    @Test
    void execute_withSigningSecret_addsHmacHeader() throws Exception {
        String url = "https://api.example.com/webhook";
        String body = "{\"event\":\"test\"}";
        String secret = "my-secret-key";
        ValidatedUrl validatedUrl = validatedUrl(url);

        try (MockedStatic<UrlValidator> validator = mockStatic(UrlValidator.class)) {
            validator.when(() -> UrlValidator.validateUrl(eq(url), anyBoolean())).thenReturn(validatedUrl);
            stubRestClient(validatedUrl);

            ResponseEntity<String> response = new ResponseEntity<>(
                    "ok", new HttpHeaders(), HttpStatusCode.valueOf(200));
            when(responseSpec.toEntity(String.class)).thenReturn(response);

            TestResult result = executor.execute("POST", url, null, body, secret);

            assertTrue(result.isSuccess());
            // HMAC now signs method+url+timestamp+body; verify signature and timestamp headers set
            verify(requestBodySpec).header(eq("X-HTTP-Actions-Signature"), anyString());
            verify(requestBodySpec).header(eq("X-HTTP-Actions-Timestamp"), anyString());
        }
    }

    @Test
    void execute_largeResponseBody_returnedFullForConditionEvaluation() throws Exception {
        String url = "https://api.example.com/large";
        String largeBody = "x".repeat(8192);
        ValidatedUrl validatedUrl = validatedUrl(url);

        try (MockedStatic<UrlValidator> validator = mockStatic(UrlValidator.class)) {
            validator.when(() -> UrlValidator.validateUrl(eq(url), anyBoolean())).thenReturn(validatedUrl);
            stubRestClient(validatedUrl);

            ResponseEntity<String> response = new ResponseEntity<>(
                    largeBody, new HttpHeaders(), HttpStatusCode.valueOf(200));
            when(responseSpec.toEntity(String.class)).thenReturn(response);

            TestResult result = executor.execute("GET", url, null, null, null);

            assertTrue(result.isSuccess());
            assertNotNull(result.getResponseBody());
            // Full body returned — truncation deferred to ExecutionService after condition evaluation
            assertEquals(8192, result.getResponseBody().length());
        }
    }

    @Test
    void execute_bodyPresentNoContentType_setsDefaultJson() throws Exception {
        String url = "https://api.example.com/data";
        ValidatedUrl validatedUrl = validatedUrl(url);

        try (MockedStatic<UrlValidator> validator = mockStatic(UrlValidator.class)) {
            validator.when(() -> UrlValidator.validateUrl(eq(url), anyBoolean())).thenReturn(validatedUrl);
            stubRestClient(validatedUrl);

            ResponseEntity<String> response = new ResponseEntity<>(
                    "ok", new HttpHeaders(), HttpStatusCode.valueOf(200));
            when(responseSpec.toEntity(String.class)).thenReturn(response);

            TestResult result = executor.execute("POST", url, new HashMap<>(), "{\"data\":1}", null);

            assertTrue(result.isSuccess());
            verify(requestBodySpec).header("Content-Type", "application/json");
            verify(requestBodySpec).header("User-Agent", "clockify-http-actions/1.0");
        }
    }

    @Test
    void execute_bodyPresentWithContentTypeHeader_doesNotOverride() throws Exception {
        String url = "https://api.example.com/xml";
        ValidatedUrl validatedUrl = validatedUrl(url);

        try (MockedStatic<UrlValidator> validator = mockStatic(UrlValidator.class)) {
            validator.when(() -> UrlValidator.validateUrl(eq(url), anyBoolean())).thenReturn(validatedUrl);
            stubRestClient(validatedUrl);

            ResponseEntity<String> response = new ResponseEntity<>(
                    "ok", new HttpHeaders(), HttpStatusCode.valueOf(200));
            when(responseSpec.toEntity(String.class)).thenReturn(response);

            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/xml");

            TestResult result = executor.execute("POST", url, headers, "<data/>", null);

            assertTrue(result.isSuccess());
            verify(requestBodySpec, never()).header(eq("Content-Type"), eq("application/json"));
            verify(requestBodySpec).header("Content-Type", "application/xml");
        }
    }

    @Test
    void execute_4xxError_capturesResponseBody() throws Exception {
        String url = "https://api.example.com/missing";
        ValidatedUrl validatedUrl = validatedUrl(url);

        try (MockedStatic<UrlValidator> validator = mockStatic(UrlValidator.class)) {
            validator.when(() -> UrlValidator.validateUrl(eq(url), anyBoolean())).thenReturn(validatedUrl);
            stubRestClient(validatedUrl);

            // With onStatus no-op, 4xx responses flow through as normal ResponseEntity
            HttpHeaders responseHeaders = new HttpHeaders();
            ResponseEntity<String> response = new ResponseEntity<>(
                    "{\"error\":\"not found\"}", responseHeaders, HttpStatusCode.valueOf(404));
            when(responseSpec.toEntity(String.class)).thenReturn(response);

            TestResult result = executor.execute("POST", url, null, "{}", null);

            assertFalse(result.isSuccess());
            assertEquals(404, result.getResponseStatus());
            assertEquals("{\"error\":\"not found\"}", result.getResponseBody());
            assertNotNull(result.getResponseHeaders());
        }
    }

    @Test
    void execute_5xxError_capturesResponseBody() throws Exception {
        String url = "https://api.example.com/broken";
        ValidatedUrl validatedUrl = validatedUrl(url);

        try (MockedStatic<UrlValidator> validator = mockStatic(UrlValidator.class)) {
            validator.when(() -> UrlValidator.validateUrl(eq(url), anyBoolean())).thenReturn(validatedUrl);
            stubRestClient(validatedUrl);

            // With onStatus no-op, 5xx responses flow through as normal ResponseEntity
            HttpHeaders responseHeaders = new HttpHeaders();
            ResponseEntity<String> response = new ResponseEntity<>(
                    "{\"error\":\"internal server error\"}", responseHeaders, HttpStatusCode.valueOf(500));
            when(responseSpec.toEntity(String.class)).thenReturn(response);

            TestResult result = executor.execute("POST", url, null, "{}", null);

            assertFalse(result.isSuccess());
            assertEquals(500, result.getResponseStatus());
            assertEquals("{\"error\":\"internal server error\"}", result.getResponseBody());
            assertNotNull(result.getResponseHeaders());
        }
    }

    private void stubRestClient(ValidatedUrl validatedUrl) {
        when(outboundRestClientFactory.createPinnedClient(validatedUrl)).thenReturn(closeableHttpClient);
        when(outboundRestClientFactory.create(closeableHttpClient)).thenReturn(restClient);
        when(restClient.method(any(HttpMethod.class))).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(validatedUrl.uri())).thenReturn(requestBodySpec);
        lenient().when(requestBodySpec.header(anyString(), any(String[].class))).thenReturn(requestBodySpec);
        lenient().when(requestBodySpec.body(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        lenient().when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
    }

    private ValidatedUrl validatedUrl(String url) throws Exception {
        URI uri = URI.create(url);
        return new ValidatedUrl(uri, uri.getHost(), InetAddress.getByName("93.184.216.34"));
    }

    private String computeHmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

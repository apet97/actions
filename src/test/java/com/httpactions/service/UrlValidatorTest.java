package com.httpactions.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class UrlValidatorTest {

    // ---- Valid URLs ----

    @Test
    void validHttpsUrlSucceeds() {
        ValidatedUrl result = UrlValidator.validateUrl("https://example.com/webhook");

        assertNotNull(result);
        assertEquals("https", result.uri().getScheme());
        assertEquals("example.com", result.host());
        assertNotNull(result.resolvedAddress());
    }

    @Test
    void validHttpUrlWithAllowHttpTrueSucceeds() {
        ValidatedUrl result = UrlValidator.validateUrl("http://example.com/webhook", true);

        assertNotNull(result);
        assertEquals("http", result.uri().getScheme());
        assertEquals("example.com", result.host());
    }

    @Test
    void validUrlReturnsValidatedUrlWithCorrectUriAndResolvedAddress() {
        ValidatedUrl result = UrlValidator.validateUrl("https://example.com/path?query=1");

        assertEquals("example.com", result.uri().getHost());
        assertEquals("/path", result.uri().getPath());
        assertEquals("query=1", result.uri().getQuery());
        assertNotNull(result.resolvedAddress());
        assertNotNull(result.resolvedAddress().getHostAddress());
    }

    // ---- Scheme restrictions ----

    @Test
    void httpUrlWithAllowHttpFalseThrowsSecurityException() {
        SecurityException ex = assertThrows(SecurityException.class,
                () -> UrlValidator.validateUrl("http://example.com/webhook", false));

        assertTrue(ex.getMessage().contains("HTTP is not allowed"));
    }

    @Test
    void fileSchemeThrowsSecurityException() {
        SecurityException ex = assertThrows(SecurityException.class,
                () -> UrlValidator.validateUrl("file:///etc/passwd"));

        assertTrue(ex.getMessage().contains("Blocked scheme"));
    }

    @Test
    void ftpSchemeThrowsSecurityException() {
        SecurityException ex = assertThrows(SecurityException.class,
                () -> UrlValidator.validateUrl("ftp://ftp.example.com/file"));

        assertTrue(ex.getMessage().contains("Blocked scheme"));
    }

    // ---- Loopback ----

    @Test
    void loopbackAddressThrowsSecurityException() {
        SecurityException ex = assertThrows(SecurityException.class,
                () -> UrlValidator.validateUrl("https://127.0.0.1/webhook"));

        assertTrue(ex.getMessage().contains("blocked"));
    }

    // ---- Private IP ranges ----

    @Test
    void privateIp10RangeThrowsSecurityException() {
        SecurityException ex = assertThrows(SecurityException.class,
                () -> UrlValidator.validateUrl("https://10.0.0.1/webhook"));

        assertTrue(ex.getMessage().contains("blocked"));
    }

    @Test
    void privateIp172_16RangeThrowsSecurityException() {
        SecurityException ex = assertThrows(SecurityException.class,
                () -> UrlValidator.validateUrl("https://172.16.0.1/webhook"));

        assertTrue(ex.getMessage().contains("blocked"));
    }

    @Test
    void privateIp192_168RangeThrowsSecurityException() {
        SecurityException ex = assertThrows(SecurityException.class,
                () -> UrlValidator.validateUrl("https://192.168.1.1/webhook"));

        assertTrue(ex.getMessage().contains("blocked"));
    }

    // ---- Link-local ----

    @Test
    void linkLocalAddressThrowsSecurityException() {
        SecurityException ex = assertThrows(SecurityException.class,
                () -> UrlValidator.validateUrl("https://169.254.1.1/webhook"));

        assertTrue(ex.getMessage().contains("blocked"));
    }

    // ---- Cloud metadata ----

    @Test
    void cloudMetadataEndpointThrowsSecurityException() {
        SecurityException ex = assertThrows(SecurityException.class,
                () -> UrlValidator.validateUrl("https://169.254.169.254/latest/meta-data/"));

        assertTrue(ex.getMessage().contains("blocked"));
    }

    // ---- Shared Address Space (100.64.0.0/10) ----

    @Test
    void sharedAddressSpace100_64ThrowsSecurityException() {
        SecurityException ex = assertThrows(SecurityException.class,
                () -> UrlValidator.validateUrl("https://100.64.0.1/webhook"));

        assertTrue(ex.getMessage().contains("blocked"));
    }

    @Test
    void sharedAddressSpace100_100ThrowsSecurityException() {
        // 100.100.x.x is within the 100.64.0.0/10 CIDR range (100.64-100.127)
        SecurityException ex = assertThrows(SecurityException.class,
                () -> UrlValidator.validateUrl("https://100.100.0.1/webhook"));

        assertTrue(ex.getMessage().contains("blocked"));
    }

    // ---- Null / blank / no host ----

    @Test
    void nullUrlThrowsSecurityException() {
        SecurityException ex = assertThrows(SecurityException.class,
                () -> UrlValidator.validateUrl(null));

        assertTrue(ex.getMessage().contains("null or blank"));
    }

    @Test
    void blankUrlThrowsSecurityException() {
        SecurityException ex = assertThrows(SecurityException.class,
                () -> UrlValidator.validateUrl("   "));

        assertTrue(ex.getMessage().contains("null or blank"));
    }

    @Test
    void urlWithNoHostThrowsSecurityException() {
        SecurityException ex = assertThrows(SecurityException.class,
                () -> UrlValidator.validateUrl("https:///path"));

        assertTrue(ex.getMessage().contains("no host"));
    }

    // ---- Default overload delegates to allowHttp=true ----

    @Test
    void defaultOverloadAllowsHttp() {
        // The single-arg overload should allow http (delegates with allowHttp=true)
        assertDoesNotThrow(() -> UrlValidator.validateUrl("http://example.com/webhook"));
    }

    // ---- Edge: upper-bound of shared address space ----

    @ParameterizedTest
    @ValueSource(strings = {
            "https://100.64.0.0/webhook",
            "https://100.95.0.1/webhook",
            "https://100.127.255.254/webhook"
    })
    void sharedAddressSpaceFullRangeBlocked(String url) {
        assertThrows(SecurityException.class, () -> UrlValidator.validateUrl(url));
    }
}

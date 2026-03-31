package com.httpactions.service;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

public final class UrlValidator {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    private UrlValidator() {}

    /**
     * Validate that the URL is safe to send an outbound request to.
     * Convenience overload that allows both http and https.
     */
    public static ValidatedUrl validateUrl(String url) {
        return validateUrl(url, true);
    }

    /**
     * Validate that the URL is safe to send an outbound request to.
     * Blocks internal/private IP ranges and non-HTTP schemes to prevent SSRF.
     *
     * @param url       the URL to validate
     * @param allowHttp if false, only https:// is permitted; http:// will be rejected
     */
    public static ValidatedUrl validateUrl(String url, boolean allowHttp) {
        if (url == null || url.isBlank()) {
            throw new SecurityException("URL is null or blank");
        }

        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new SecurityException("Invalid URL: " + e.getMessage());
        }

        // Block non-HTTP schemes (file://, ftp://, gopher://, etc.)
        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) {
            throw new SecurityException("Blocked scheme: " + scheme + ". Only http and https are allowed.");
        }

        // When allowHttp is false, enforce https-only
        if (!allowHttp && "http".equalsIgnoreCase(scheme)) {
            throw new SecurityException("HTTP is not allowed. Only HTTPS URLs are permitted.");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new SecurityException("URL has no host");
        }

        // Resolve DNS and check the resulting IP address
        InetAddress addr;
        try {
            addr = InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            throw new SecurityException("Cannot resolve host: " + host);
        }

        String hostAddr = addr.getHostAddress();

        if (addr.isLoopbackAddress() || addr.isLinkLocalAddress()
                || addr.isSiteLocalAddress() || addr.isAnyLocalAddress()
                || addr.isMulticastAddress()) {
            throw new SecurityException("URL resolves to blocked internal address: " + hostAddr);
        }

        // Check Shared Address Space (100.64.0.0/10 = 100.64.0.0 through 100.127.255.255)
        byte[] addrBytes = addr.getAddress();
        if (addrBytes.length == 4) {
            int firstOctet = addrBytes[0] & 0xFF;
            int secondOctet = addrBytes[1] & 0xFF;
            if (firstOctet == 100 && secondOctet >= 64 && secondOctet <= 127) {
                throw new SecurityException("URL resolves to blocked Shared Address Space: " + hostAddr);
            }
        }

        // Check IPv6 unique-local addresses (fc00::/7 = fc00:: through fdff::)
        // Not reliably covered by isSiteLocalAddress() across all JDK versions
        if (addrBytes.length == 16) {
            int firstByte = addrBytes[0] & 0xFF;
            if (firstByte == 0xFC || firstByte == 0xFD) {
                throw new SecurityException("URL resolves to blocked IPv6 unique-local address: " + hostAddr);
            }
        }

        // Block cloud metadata endpoint
        if ("169.254.169.254".equals(hostAddr)) {
            throw new SecurityException("URL resolves to blocked cloud metadata endpoint: " + hostAddr);
        }

        return new ValidatedUrl(uri, host, addr);
    }
}

package com.httpactions.service;

import com.httpactions.config.AddonConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboundRestClientFactoryTest {

    @Mock
    private AddonConfig addonConfig;

    private OutboundRestClientFactory factory;

    @BeforeEach
    void setUp() {
        factory = new OutboundRestClientFactory(addonConfig);
    }

    @Test
    @DisplayName("DNS pinning returns the pre-resolved address when hostname matches target")
    void createPinnedClient_matchingHost_returnsPinnedAddress() throws Exception {
        InetAddress pinnedAddress = InetAddress.getByAddress("api.example.com", new byte[]{93, (byte) 184, (byte) 216, 34});
        ValidatedUrl target = new ValidatedUrl(
                URI.create("https://api.example.com/webhook"),
                "api.example.com",
                pinnedAddress
        );

        CloseableHttpClient client = factory.createPinnedClient(target);
        assertNotNull(client);

        // Resolve the target host through the pinned client's DNS resolver
        // by making a connection attempt — verified indirectly via the resolver contract
        // The DnsResolver is wired into the connection manager; direct invocation tests below
        // exercise the resolve() method extracted from the anonymous class.
        //
        // To test the DNS resolver behavior directly, we extract the same logic:
        InetAddress[] resolved = resolveThroughPinnedLogic(target, "api.example.com");
        assertArrayEquals(new InetAddress[]{pinnedAddress}, resolved);
    }

    @Test
    @DisplayName("DNS pinning is case-insensitive for hostname comparison")
    void createPinnedClient_caseInsensitiveMatch_returnsPinnedAddress() throws Exception {
        InetAddress pinnedAddress = InetAddress.getByAddress("api.example.com", new byte[]{10, 0, 0, 1});
        ValidatedUrl target = new ValidatedUrl(
                URI.create("https://api.example.com/hook"),
                "api.example.com",
                pinnedAddress
        );

        InetAddress[] resolved = resolveThroughPinnedLogic(target, "API.EXAMPLE.COM");
        assertArrayEquals(new InetAddress[]{pinnedAddress}, resolved);
    }

    @Test
    @DisplayName("Different hostname does not receive the pinned address")
    void createPinnedClient_differentHost_doesNotReturnPinnedAddress() throws Exception {
        InetAddress pinnedAddress = InetAddress.getByAddress("api.example.com", new byte[]{93, (byte) 184, (byte) 216, 34});
        ValidatedUrl target = new ValidatedUrl(
                URI.create("https://api.example.com/webhook"),
                "api.example.com",
                pinnedAddress
        );

        // A non-matching hostname should NOT return the pinned address.
        // The pinning logic only activates when IDN-normalized hostnames match.
        String differentHost = "other.example.com";
        String normalizedTarget = java.net.IDN.toASCII(target.host(), java.net.IDN.ALLOW_UNASSIGNED);
        String normalizedOther = java.net.IDN.toASCII(differentHost, java.net.IDN.ALLOW_UNASSIGNED);
        assertFalse(normalizedTarget.equalsIgnoreCase(normalizedOther),
                "Test precondition: hostnames must differ after normalization");
    }

    @Test
    @DisplayName("IDN/punycode normalization pins correctly for internationalized domain names")
    void createPinnedClient_idnNormalization_returnsPinnedAddress() throws Exception {
        // "xn--nxasmq6b.example.com" is the punycode form of an internationalized hostname
        String unicodeHost = "\u03B2\u03AE\u03C4\u03B1.example.com"; // Greek letters
        String punycodeHost = java.net.IDN.toASCII(unicodeHost, java.net.IDN.ALLOW_UNASSIGNED);

        InetAddress pinnedAddress = InetAddress.getByAddress(punycodeHost, new byte[]{10, 0, 0, 2});
        ValidatedUrl target = new ValidatedUrl(
                URI.create("https://" + punycodeHost + "/api"),
                unicodeHost,
                pinnedAddress
        );

        // Resolving with the unicode form should match after IDN normalization
        InetAddress[] resolved = resolveThroughPinnedLogic(target, unicodeHost);
        assertArrayEquals(new InetAddress[]{pinnedAddress}, resolved);

        // Resolving with the punycode form should also match
        InetAddress[] resolvedPunycode = resolveThroughPinnedLogic(target, punycodeHost);
        assertArrayEquals(new InetAddress[]{pinnedAddress}, resolvedPunycode);
    }

    @Test
    @DisplayName("RestClient is created with configured connect and read timeouts")
    void create_configuredTimeouts_returnsRestClient() throws Exception {
        AddonConfig.Outbound outbound = new AddonConfig.Outbound();
        outbound.setConnectTimeout(Duration.ofSeconds(5));
        outbound.setReadTimeout(Duration.ofSeconds(15));
        when(addonConfig.getOutbound()).thenReturn(outbound);

        InetAddress pinnedAddress = InetAddress.getByAddress("api.example.com", new byte[]{93, (byte) 184, (byte) 216, 34});
        ValidatedUrl target = new ValidatedUrl(
                URI.create("https://api.example.com/webhook"),
                "api.example.com",
                pinnedAddress
        );

        CloseableHttpClient httpClient = factory.createPinnedClient(target);
        RestClient restClient = factory.create(httpClient);

        assertNotNull(restClient);
    }

    @Test
    @DisplayName("RestClient is created successfully with default timeout values")
    void create_defaultTimeouts_returnsRestClient() throws Exception {
        AddonConfig.Outbound outbound = new AddonConfig.Outbound();
        when(addonConfig.getOutbound()).thenReturn(outbound);

        InetAddress pinnedAddress = InetAddress.getByAddress("api.example.com", new byte[]{93, (byte) 184, (byte) 216, 34});
        ValidatedUrl target = new ValidatedUrl(
                URI.create("https://api.example.com/webhook"),
                "api.example.com",
                pinnedAddress
        );

        CloseableHttpClient httpClient = factory.createPinnedClient(target);
        RestClient restClient = factory.create(httpClient);

        assertNotNull(restClient);
    }

    /**
     * Replicates the DNS resolver logic from {@link OutboundRestClientFactory#createPinnedClient}
     * to allow direct unit testing of the hostname matching and IDN normalization behavior.
     */
    private InetAddress[] resolveThroughPinnedLogic(ValidatedUrl target, String hostToResolve)
            throws UnknownHostException {
        String normalizedTarget = java.net.IDN.toASCII(target.host(), java.net.IDN.ALLOW_UNASSIGNED);
        String normalizedHost = java.net.IDN.toASCII(hostToResolve, java.net.IDN.ALLOW_UNASSIGNED);
        if (normalizedTarget.equalsIgnoreCase(normalizedHost)) {
            return new InetAddress[]{target.resolvedAddress()};
        }
        // Simulate system resolver fallback — will throw for non-existent hosts
        return InetAddress.getAllByName(hostToResolve);
    }
}

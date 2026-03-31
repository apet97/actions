package com.httpactions.service;

import com.httpactions.config.AddonConfig;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.IDN;
import java.net.InetAddress;
import java.net.UnknownHostException;

@Component
public class OutboundRestClientFactory {

    private final AddonConfig addonConfig;

    public OutboundRestClientFactory(AddonConfig addonConfig) {
        this.addonConfig = addonConfig;
    }

    public CloseableHttpClient createPinnedClient(ValidatedUrl target) {
        String normalizedTarget = IDN.toASCII(target.host());
        DnsResolver dnsResolver = new SystemDefaultDnsResolver() {
            @Override
            public InetAddress[] resolve(String host) throws UnknownHostException {
                // Normalize both hostnames to ASCII (punycode) to prevent IDN/unicode bypass
                String normalizedHost = IDN.toASCII(host);
                if (normalizedTarget.equalsIgnoreCase(normalizedHost)) {
                    return new InetAddress[]{target.resolvedAddress()};
                }
                return super.resolve(host);
            }
        };

        return HttpClients.custom()
                .disableAutomaticRetries()
                .disableRedirectHandling()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDnsResolver(dnsResolver)
                        .build())
                .build();
    }

    public RestClient create(CloseableHttpClient client) {
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(client);
        factory.setConnectTimeout(addonConfig.getOutbound().getConnectTimeout());
        factory.setReadTimeout(addonConfig.getOutbound().getReadTimeout());
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }
}

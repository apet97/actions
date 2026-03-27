package com.httpactions.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "addon")
public class AddonConfig {

    private String key;
    private String baseUrl;
    private String tokenEncryptionKey;
    private String tokenEncryptionSalt;
    private Outbound outbound = new Outbound();

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getTokenEncryptionKey() { return tokenEncryptionKey; }
    public void setTokenEncryptionKey(String tokenEncryptionKey) { this.tokenEncryptionKey = tokenEncryptionKey; }

    public String getTokenEncryptionSalt() { return tokenEncryptionSalt; }
    public void setTokenEncryptionSalt(String tokenEncryptionSalt) { this.tokenEncryptionSalt = tokenEncryptionSalt; }

    public Outbound getOutbound() { return outbound; }
    public void setOutbound(Outbound outbound) { this.outbound = outbound; }

    public static class Outbound {
        private Duration connectTimeout = Duration.ofSeconds(10);
        private Duration readTimeout = Duration.ofSeconds(30);
        private int rateLimitPerWorkspace = 10;
        private boolean allowHttp = false;

        public Duration getConnectTimeout() { return connectTimeout; }
        public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }

        public Duration getReadTimeout() { return readTimeout; }
        public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }

        public int getRateLimitPerWorkspace() { return rateLimitPerWorkspace; }
        public void setRateLimitPerWorkspace(int rateLimitPerWorkspace) { this.rateLimitPerWorkspace = rateLimitPerWorkspace; }

        public boolean isAllowHttp() { return allowHttp; }
        public void setAllowHttp(boolean allowHttp) { this.allowHttp = allowHttp; }
    }
}

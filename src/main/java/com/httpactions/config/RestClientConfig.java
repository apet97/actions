package com.httpactions.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient restClient(AddonConfig addonConfig) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) addonConfig.getOutbound().getConnectTimeout().toMillis());
        factory.setReadTimeout((int) addonConfig.getOutbound().getReadTimeout().toMillis());

        return RestClient.builder()
                .requestFactory(factory)
                .defaultHeader("User-Agent", addonConfig.getKey() + "/1.0")
                .build();
    }
}

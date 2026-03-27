package com.httpactions.config;

import com.google.gson.Gson;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Shared Gson instance for the application.
 */
public final class GsonProvider {

    private static final Gson GSON = new Gson();

    private GsonProvider() {}

    public static Gson get() {
        return GSON;
    }

    @Configuration
    public static class GsonConfig {
        @Bean
        public Gson gson() {
            return GSON;
        }
    }
}

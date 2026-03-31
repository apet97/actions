package com.httpactions.config;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * Shared Gson instance and type tokens for the application.
 */
public final class GsonProvider {

    private static final Gson GSON = new Gson();

    /** Reusable type token for Map&lt;String, String&gt; (e.g. encrypted headers). */
    public static final Type STRING_MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();

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

package com.httpactions.integration;

import com.httpactions.repository.WebhookEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
class FlywayIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("addon.token-encryption-key",
                () -> "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        registry.add("addon.token-encryption-salt", () -> "0123456789abcdef0123456789abcdef");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WebhookEventRepository webhookEventRepository;

    @Test
    @Transactional
    @DisplayName("Application boots against fresh Postgres, schema hardening is applied, and idempotency insert works")
    void applicationBootsAndIdempotencyInsertWorks() {
        assertEquals(256, jdbcTemplate.queryForObject(
                """
                SELECT character_maximum_length
                FROM information_schema.columns
                WHERE table_name = 'webhook_events' AND column_name = 'event_id'
                """,
                Integer.class));
        assertEquals("NO", jdbcTemplate.queryForObject(
                """
                SELECT is_nullable
                FROM information_schema.columns
                WHERE table_name = 'webhook_events' AND column_name = 'received_at'
                """,
                String.class));
        assertEquals("jsonb", jdbcTemplate.queryForObject(
                """
                SELECT udt_name
                FROM information_schema.columns
                WHERE table_name = 'actions' AND column_name = 'success_conditions'
                """,
                String.class));
        assertEquals(1L, jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM pg_constraint
                WHERE conname = 'chk_chain_order'
                """,
                Long.class));

        assertEquals(1, webhookEventRepository.insertIfAbsent("ws-it", "evt-123", "NEW_TIME_ENTRY"));
        assertEquals(0, webhookEventRepository.insertIfAbsent("ws-it", "evt-123", "NEW_TIME_ENTRY"));
    }
}

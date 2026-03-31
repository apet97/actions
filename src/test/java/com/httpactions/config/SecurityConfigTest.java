package com.httpactions.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SecurityConfigTest {

    // --- Static resource / health endpoint accessibility ---
    // SecurityConfig does not configure Spring Security HTTP filters (no SecurityFilterChain bean).
    // It provides encryption beans and a PublicKey bean. Accessibility of endpoints is not restricted
    // by Spring Security because there is no spring-boot-starter-security dependency — only
    // spring-security-crypto is used for AES-256 encryption.
    //
    // We verify the configuration validation logic, which is the security-critical path in this class.

    @Test
    void validateEncryptionConfig_validConfig_doesNotThrow() {
        AddonConfig config = new AddonConfig();
        // 64-char hex key
        config.setTokenEncryptionKey("a".repeat(64));
        // 32-char hex salt
        config.setTokenEncryptionSalt("b".repeat(32));

        SecurityConfig securityConfig = new SecurityConfig(config);

        assertDoesNotThrow(securityConfig::validateEncryptionConfig);
    }

    @Test
    void validateEncryptionConfig_nullKey_throws() {
        AddonConfig config = new AddonConfig();
        config.setTokenEncryptionKey(null);
        config.setTokenEncryptionSalt("b".repeat(32));

        SecurityConfig securityConfig = new SecurityConfig(config);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                securityConfig::validateEncryptionConfig);
        assertTrue(ex.getMessage().contains("TOKEN_ENCRYPTION_KEY"));
    }

    @Test
    void validateEncryptionConfig_blankKey_throws() {
        AddonConfig config = new AddonConfig();
        config.setTokenEncryptionKey("   ");
        config.setTokenEncryptionSalt("b".repeat(32));

        SecurityConfig securityConfig = new SecurityConfig(config);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                securityConfig::validateEncryptionConfig);
        assertTrue(ex.getMessage().contains("TOKEN_ENCRYPTION_KEY"));
    }

    @Test
    void validateEncryptionConfig_shortKey_throws() {
        AddonConfig config = new AddonConfig();
        config.setTokenEncryptionKey("a".repeat(63)); // one short
        config.setTokenEncryptionSalt("b".repeat(32));

        SecurityConfig securityConfig = new SecurityConfig(config);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                securityConfig::validateEncryptionConfig);
        assertTrue(ex.getMessage().contains("TOKEN_ENCRYPTION_KEY"));
    }

    @Test
    void validateEncryptionConfig_nullSalt_throws() {
        AddonConfig config = new AddonConfig();
        config.setTokenEncryptionKey("a".repeat(64));
        config.setTokenEncryptionSalt(null);

        SecurityConfig securityConfig = new SecurityConfig(config);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                securityConfig::validateEncryptionConfig);
        assertTrue(ex.getMessage().contains("TOKEN_ENCRYPTION_SALT"));
    }

    @Test
    void validateEncryptionConfig_blankSalt_throws() {
        AddonConfig config = new AddonConfig();
        config.setTokenEncryptionKey("a".repeat(64));
        config.setTokenEncryptionSalt("  ");

        SecurityConfig securityConfig = new SecurityConfig(config);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                securityConfig::validateEncryptionConfig);
        assertTrue(ex.getMessage().contains("TOKEN_ENCRYPTION_SALT"));
    }

    @Test
    void validateEncryptionConfig_shortSalt_throws() {
        AddonConfig config = new AddonConfig();
        config.setTokenEncryptionKey("a".repeat(64));
        config.setTokenEncryptionSalt("b".repeat(31)); // one short

        SecurityConfig securityConfig = new SecurityConfig(config);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                securityConfig::validateEncryptionConfig);
        assertTrue(ex.getMessage().contains("TOKEN_ENCRYPTION_SALT"));
    }

    @Test
    void validateEncryptionConfig_longerThanMinimum_doesNotThrow() {
        AddonConfig config = new AddonConfig();
        config.setTokenEncryptionKey("a".repeat(128));
        config.setTokenEncryptionSalt("b".repeat(64));

        SecurityConfig securityConfig = new SecurityConfig(config);

        assertDoesNotThrow(securityConfig::validateEncryptionConfig);
    }

    // --- textEncryptor bean ---

    @Test
    void textEncryptor_createdSuccessfully_withValidConfig() {
        AddonConfig config = new AddonConfig();
        // Must be valid hex for Encryptors.text()
        config.setTokenEncryptionKey("deadbeef0123456789abcdef0123456789abcdef0123456789abcdef01234567");
        config.setTokenEncryptionSalt("ab01cd23ef4567890123456789abcdef");

        SecurityConfig securityConfig = new SecurityConfig(config);

        assertDoesNotThrow(securityConfig::textEncryptor);
        assertNotNull(securityConfig.textEncryptor());
    }

    // --- clockifyPublicKey bean ---

    @Test
    void clockifyPublicKey_loadsFromClasspath() throws Exception {
        AddonConfig config = new AddonConfig();
        config.setTokenEncryptionKey("a".repeat(64));
        config.setTokenEncryptionSalt("b".repeat(32));

        SecurityConfig securityConfig = new SecurityConfig(config);

        java.security.PublicKey key = securityConfig.clockifyPublicKey();
        assertNotNull(key);
        assertEquals("RSA", key.getAlgorithm());
    }
}

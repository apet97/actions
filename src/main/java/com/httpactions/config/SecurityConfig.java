package com.httpactions.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Configuration
public class SecurityConfig {

    private final AddonConfig addonConfig;

    public SecurityConfig(AddonConfig addonConfig) {
        this.addonConfig = addonConfig;
    }

    @PostConstruct
    void validateEncryptionConfig() {
        String key = addonConfig.getTokenEncryptionKey();
        String salt = addonConfig.getTokenEncryptionSalt();

        if (key == null || key.isBlank() || key.length() < 64) {
            throw new IllegalStateException(
                "TOKEN_ENCRYPTION_KEY must be a hex string of at least 64 characters");
        }
        if (salt == null || salt.isBlank() || salt.length() < 32) {
            throw new IllegalStateException(
                "TOKEN_ENCRYPTION_SALT must be a hex string of at least 32 characters");
        }
    }

    @Bean
    public TextEncryptor textEncryptor() {
        return Encryptors.text(addonConfig.getTokenEncryptionKey(), addonConfig.getTokenEncryptionSalt());
    }

    @Bean
    public PublicKey clockifyPublicKey() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        ClassPathResource resource = new ClassPathResource("clockify-public-key.pem");
        String pem = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        String publicKeyPEM = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        byte[] decoded = Base64.getDecoder().decode(publicKeyPEM);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decoded);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(keySpec);
    }
}

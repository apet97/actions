package com.httpactions.service;

import com.google.gson.Gson;
import com.httpactions.config.AddonConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;

import java.security.*;
import java.security.spec.RSAKeyGenParameterSpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    private static final Gson GSON = new Gson();
    private static final String ADDON_KEY = "test-addon-key";

    private static KeyPair testKeyPair;
    private static PrivateKey testPrivateKey;
    private static PublicKey testPublicKey;

    @Mock
    private AddonConfig addonConfig;

    private TokenService tokenService;

    @BeforeAll
    static void generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(new RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4));
        testKeyPair = generator.generateKeyPair();
        testPrivateKey = testKeyPair.getPrivate();
        testPublicKey = testKeyPair.getPublic();
    }

    @BeforeEach
    void setUp() {
        TextEncryptor encryptor = Encryptors.text("deadbeef0123456789abcdef01234567", "ab01cd23ef45");
        tokenService = new TokenService(testPublicKey, encryptor, addonConfig);
    }

    // --- Helper ---

    private static String createJwt(Map<String, Object> claims) throws Exception {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes());
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(GSON.toJson(claims).getBytes());
        String content = header + "." + payload;

        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(testPrivateKey);
        sig.update(content.getBytes());
        String signature = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(sig.sign());

        return content + "." + signature;
    }

    private static Map<String, Object> validClaims() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("iss", "clockify");
        claims.put("type", "addon");
        claims.put("sub", ADDON_KEY);
        claims.put("workspaceId", "ws1");
        claims.put("backendUrl", "https://api.clockify.me/api");
        claims.put("exp", (System.currentTimeMillis() / 1000) + 3600); // 1 hour from now
        return claims;
    }

    // --- verifyAndParseClaims tests ---

    @Test
    void verifyAndParseClaims_validJwt_returnsClaims() throws Exception {
        when(addonConfig.getKey()).thenReturn(ADDON_KEY);

        String jwt = createJwt(validClaims());
        Map<String, Object> result = tokenService.verifyAndParseClaims(jwt);

        assertNotNull(result);
        assertEquals("clockify", result.get("iss"));
        assertEquals("addon", result.get("type"));
        assertEquals(ADDON_KEY, result.get("sub"));
        assertEquals("ws1", result.get("workspaceId"));
        assertEquals("https://api.clockify.me/api", result.get("backendUrl"));
    }

    @Test
    void verifyAndParseClaims_invalidSignature_returnsNull() throws Exception {
        String jwt = createJwt(validClaims());
        // Tamper with the signature: flip a character
        String[] parts = jwt.split("\\.");
        byte[] sigBytes = Base64.getUrlDecoder().decode(parts[2]);
        sigBytes[0] = (byte) (sigBytes[0] ^ 0xFF);
        String tamperedSig = Base64.getUrlEncoder().withoutPadding().encodeToString(sigBytes);
        String tamperedJwt = parts[0] + "." + parts[1] + "." + tamperedSig;

        assertNull(tokenService.verifyAndParseClaims(tamperedJwt));
    }

    @Test
    void verifyAndParseClaims_expiredJwt_returnsNull() throws Exception {
        when(addonConfig.getKey()).thenReturn(ADDON_KEY);

        Map<String, Object> claims = validClaims();
        claims.put("exp", (System.currentTimeMillis() / 1000) - 3600); // 1 hour ago

        String jwt = createJwt(claims);
        assertNull(tokenService.verifyAndParseClaims(jwt));
    }

    @Test
    void verifyAndParseClaims_wrongIssuer_returnsNull() throws Exception {
        Map<String, Object> claims = validClaims();
        claims.put("iss", "not-clockify");

        String jwt = createJwt(claims);
        assertNull(tokenService.verifyAndParseClaims(jwt));
    }

    @Test
    void verifyAndParseClaims_wrongType_returnsNull() throws Exception {
        Map<String, Object> claims = validClaims();
        claims.put("type", "user");

        String jwt = createJwt(claims);
        assertNull(tokenService.verifyAndParseClaims(jwt));
    }

    @Test
    void verifyAndParseClaims_wrongSubject_returnsNull() throws Exception {
        when(addonConfig.getKey()).thenReturn(ADDON_KEY);

        Map<String, Object> claims = validClaims();
        claims.put("sub", "wrong-addon-key");

        String jwt = createJwt(claims);
        assertNull(tokenService.verifyAndParseClaims(jwt));
    }

    @Test
    void verifyAndParseClaims_malformedJwt_returnsNull() {
        assertNull(tokenService.verifyAndParseClaims("not.a.valid.jwt.at.all"));
        assertNull(tokenService.verifyAndParseClaims("onlyonepart"));
        assertNull(tokenService.verifyAndParseClaims("two.parts"));
    }

    // --- normalizeClaims tests (tested through verifyAndParseClaims) ---

    @Test
    void normalizeClaims_activeWs_mappedToWorkspaceId() throws Exception {
        when(addonConfig.getKey()).thenReturn(ADDON_KEY);

        Map<String, Object> claims = validClaims();
        claims.remove("workspaceId");
        claims.put("activeWs", "ws-from-activeWs");

        String jwt = createJwt(claims);
        Map<String, Object> result = tokenService.verifyAndParseClaims(jwt);

        assertNotNull(result);
        assertEquals("ws-from-activeWs", result.get("workspaceId"));
    }

    @Test
    void normalizeClaims_apiUrl_mappedToBackendUrl() throws Exception {
        when(addonConfig.getKey()).thenReturn(ADDON_KEY);

        Map<String, Object> claims = validClaims();
        claims.remove("backendUrl");
        claims.put("apiUrl", "https://api.clockify.me/api");

        String jwt = createJwt(claims);
        Map<String, Object> result = tokenService.verifyAndParseClaims(jwt);

        assertNotNull(result);
        assertEquals("https://api.clockify.me/api", result.get("backendUrl"));
    }

    @Test
    void normalizeClaims_backendUrl_pathNormalized() throws Exception {
        when(addonConfig.getKey()).thenReturn(ADDON_KEY);

        Map<String, Object> claims = validClaims();
        claims.put("backendUrl", "https://api.clockify.me/api/v1");

        String jwt = createJwt(claims);
        Map<String, Object> result = tokenService.verifyAndParseClaims(jwt);

        assertNotNull(result);
        assertEquals("https://api.clockify.me/api", result.get("backendUrl"));
    }

    // --- encrypt / decrypt tests ---

    @Test
    void encrypt_decrypt_roundTrip() {
        String plaintext = "secret-installation-token-12345";
        String encrypted = tokenService.encrypt(plaintext);

        assertNotNull(encrypted);
        assertNotEquals(plaintext, encrypted);

        String decrypted = tokenService.decrypt(encrypted);
        assertEquals(plaintext, decrypted);
    }

    @Test
    void encrypt_null_returnsNull() {
        assertNull(tokenService.encrypt(null));
    }

    @Test
    void encrypt_blank_returnsNull() {
        assertNull(tokenService.encrypt(""));
        assertNull(tokenService.encrypt("   "));
    }

    @Test
    void decrypt_null_returnsNull() {
        assertNull(tokenService.decrypt(null));
    }

    @Test
    void decrypt_blank_returnsNull() {
        assertNull(tokenService.decrypt(""));
        assertNull(tokenService.decrypt("   "));
    }

    @Test
    void verifyAndParseClaims_wrongAlgorithm_returnsNull() throws Exception {
        // Build a JWT with HS256 header instead of RS256
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes());
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(GSON.toJson(validClaims()).getBytes());
        String content = header + "." + payload;

        // Sign with RS256 anyway (the alg header claims HS256 but we need a valid-length signature)
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(testPrivateKey);
        sig.update(content.getBytes());
        String signature = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(sig.sign());

        String jwt = content + "." + signature;

        // Should be rejected because alg header is not RS256
        assertNull(tokenService.verifyAndParseClaims(jwt));
    }
}

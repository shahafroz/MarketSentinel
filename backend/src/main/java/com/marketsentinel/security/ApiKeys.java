package com.marketsentinel.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * API key helpers. A key is 32 random bytes; the database stores only its SHA-256 hash, so a
 * database leak does not leak usable keys. (A fast hash is fine here: unlike passwords, the keys
 * are long and random, so they cannot be guessed.)
 */
public final class ApiKeys {

    private static final SecureRandom RANDOM = new SecureRandom();

    private ApiKeys() {
    }

    public static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return "msk_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String prefix(String key) {
        return key.substring(0, Math.min(12, key.length()));
    }
}

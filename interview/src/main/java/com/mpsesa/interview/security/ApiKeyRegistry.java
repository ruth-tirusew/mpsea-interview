package com.mpsesa.interview.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Holds only SHA-256 hashes of the configured keys; fails startup if security is not configured. */
@Component
public class ApiKeyRegistry {
    public record Client(String id, boolean admin) {
    }

    private final Map<String, Client> byHash = new HashMap<>();

    public ApiKeyRegistry(SecurityProperties props) {
        add(props.userKeys(), false);
        add(props.adminKeys(), true);
        if (byHash.isEmpty()) {
            throw new IllegalStateException("no API keys configured (WALLET_USER_API_KEYS / WALLET_ADMIN_API_KEYS)");
        }
        if (props.callback() == null || props.callback().secret() == null || props.callback().secret().isBlank()) {
            throw new IllegalStateException("callback secret not configured (BANK_CALLBACK_SECRET)");
        }
    }

    public Optional<Client> find(String presentedKey) {
        if (presentedKey == null || presentedKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byHash.get(hash(presentedKey)));
    }

    private void add(List<String> keys, boolean admin) {
        if (keys == null) {
            return;
        }
        for (String key : keys) {
            if (key == null || key.isBlank()) {
                continue;
            }
            String h = hash(key.trim());
            // the id is a fingerprint, never the key itself, so it is safe to log
            if (byHash.put(h, new Client((admin ? "admin:" : "user:") + h.substring(0, 8), admin)) != null) {
                throw new IllegalStateException("the same API key is configured twice");
            }
        }
    }

    private static String hash(String key) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

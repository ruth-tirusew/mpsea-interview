package com.mpsesa.interview.security;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** signature = hex(HMAC-SHA256(secret, timestamp + "." + rawBody)); timestamp is epoch seconds. */
public final class CallbackSignature {
    private CallbackSignature() {
    }

    public static String sign(String secret, String timestamp, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean matches(String secret, String timestamp, byte[] body, String presented) {
        if (presented == null) {
            return false;
        }
        byte[] expected = sign(secret, timestamp, body).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, presented.getBytes(StandardCharsets.UTF_8));
    }
}

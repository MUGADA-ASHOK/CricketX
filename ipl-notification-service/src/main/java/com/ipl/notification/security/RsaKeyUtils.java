package com.ipl.notification.security;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class RsaKeyUtils {

    private RsaKeyUtils() {
    }

    public static PublicKey parsePublicKey(String base64Key) {
        try {
            String cleaned = base64Key
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] keyBytes = Base64.getDecoder().decode(cleaned);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
            return KeyFactory.getInstance("RSA").generatePublic(keySpec);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to parse RSA public key: " + ex.getMessage(), ex);
        }
    }
}


package com.oncue.common.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/** Loads the PKCS#8 RSA private key used only for voice connection tokens. */
public final class RsaPrivateKeyLoader {

    private RsaPrivateKeyLoader() {
    }

    public static PrivateKey load(String keyMaterial, String keyFile) {
        String pem = keyMaterial;
        if (isBlank(pem) && !isBlank(keyFile)) {
            try {
                pem = Files.readString(Path.of(keyFile));
            } catch (IOException exception) {
                throw new IllegalArgumentException("Could not read voice JWT private key file", exception);
            }
        }
        if (isBlank(pem)) {
            throw new IllegalArgumentException(
                    "Voice JWT private key is required via VOICE_JWT_PRIVATE_KEY or VOICE_JWT_PRIVATE_KEY_FILE");
        }

        String encoded = pem.replace("\\n", "\n")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        try {
            byte[] keyBytes = Base64.getDecoder().decode(encoded);
            return KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Voice JWT private key must be a valid PKCS#8 RSA key", exception);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

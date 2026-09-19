package com.oncue.common.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/** Loads the PKCS#8 EC private key used by the Apple APNs Auth Key. */
public final class EcPrivateKeyLoader {

    private EcPrivateKeyLoader() {
    }

    public static PrivateKey load(String keyMaterial, String keyFile) {
        String pem = keyMaterial;
        if (isBlank(pem) && !isBlank(keyFile)) {
            try {
                pem = Files.readString(Path.of(keyFile));
            } catch (IOException exception) {
                throw new IllegalArgumentException("Could not read APNs private key file", exception);
            }
        }
        if (isBlank(pem)) {
            throw new IllegalArgumentException(
                    "APNs private key is required via APNS_PRIVATE_KEY or APNS_PRIVATE_KEY_FILE");
        }

        String encoded = pem.replace("\\n", "\n")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        try {
            byte[] keyBytes = Base64.getDecoder().decode(encoded);
            return KeyFactory.getInstance("EC")
                    .generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        } catch (Exception exception) {
            throw new IllegalArgumentException("APNs private key must be a valid PKCS#8 EC key", exception);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

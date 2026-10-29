
package com.tdsi.memotheque.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * [M3] Empreinte SHA-256 d'un fichier (intégrité des PDF).
 */
public final class HashUtil {

    private HashUtil() { }

    public static String sha256(Path fichier) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            try (InputStream input = Files.newInputStream(fichier)) {
                byte[] buffer = new byte[8192];
                int octetsLus;

                while ((octetsLus = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, octetsLus);
                }
            }

            return HexFormat.of().formatHex(digest.digest());

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                "L'algorithme SHA-256 n'est pas disponible.", e
            );
        }
    }
}

package com.tdsi.memotheque.util;

import java.io.IOException;
import java.nio.file.Path;

/**
 * [M3] Empreinte SHA-256 d'un fichier (intégrité des PDF).
 * À faire : MessageDigest.getInstance("SHA-256"), lecture par blocs, retour en hexadécimal.
 */
public final class HashUtil {

    private HashUtil() { }

    public static String sha256(Path fichier) throws IOException {
        throw new UnsupportedOperationException("TODO M3");
    }
}

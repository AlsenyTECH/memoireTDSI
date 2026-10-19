package com.tdsi.memotheque.util;

/**
 * [M2] Hachage des mots de passe.
 * À faire : PBKDF2WithHmacSHA256, 120 000 itérations, sel aléatoire de 16 octets (SecureRandom),
 * encodage Base64, comparaison en temps constant (MessageDigest.isEqual).
 */
public final class PasswordUtil {

    private PasswordUtil() { }

    /** Génère un sel aléatoire encodé en Base64. */
    public static String genererSel() {
        throw new UnsupportedOperationException("TODO M2");
    }

    /** Retourne le hash (Base64) du mot de passe avec le sel donné. */
    public static String hasher(String motDePasse, String sel) {
        throw new UnsupportedOperationException("TODO M2");
    }

    /** Vérifie un mot de passe saisi par rapport au hash et au sel enregistrés. */
    public static boolean verifier(String motDePasse, String sel, String hashAttendu) {
        throw new UnsupportedOperationException("TODO M2");
    }
}

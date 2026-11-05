package com.tdsi.memotheque.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Mots de passe à usage unique basés sur le temps (TOTP, RFC 6238), compatibles avec
 * Google Authenticator, Microsoft Authenticator, FreeOTP, Aegis...
 * HMAC-SHA1, pas de 30 secondes, 6 chiffres ; le secret (160 bits) est échangé en Base32 (RFC 4648).
 */
public final class Totp {

    public static final int PAS_SECONDES = 30;
    public static final int CHIFFRES = 6;
    /** Tolérance d'un pas avant/après (décalage d'horloge du téléphone). */
    public static final int FENETRE = 1;

    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom ALEA = new SecureRandom();

    private Totp() { }

    /** Nouveau secret aléatoire de 20 octets. */
    public static byte[] nouveauSecret() {
        byte[] s = new byte[20];
        ALEA.nextBytes(s);
        return s;
    }

    /** Pas de temps courant : floor(t / 30). */
    public static long pas(long epochSecondes) {
        return Math.floorDiv(epochSecondes, PAS_SECONDES);
    }

    /** Code à {@code chiffres} chiffres pour un pas donné (HOTP, RFC 4226, troncature dynamique). */
    public static String code(byte[] secret, long pas, int chiffres) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] h = mac.doFinal(java.nio.ByteBuffer.allocate(8).putLong(pas).array());
            int o = h[h.length - 1] & 0x0F;
            int binaire = ((h[o] & 0x7F) << 24) | ((h[o + 1] & 0xFF) << 16) | ((h[o + 2] & 0xFF) << 8) | (h[o + 3] & 0xFF);
            int mod = (int) Math.pow(10, chiffres);
            return String.format("%0" + chiffres + "d", binaire % mod);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA1 indisponible", e);
        }
    }

    public static String code(byte[] secret, long pas) {
        return code(secret, pas, CHIFFRES);
    }

    /**
     * Vérifie un code saisi dans la fenêtre de tolérance.
     * Retourne le pas reconnu (à mémoriser contre le rejeu), ou -1 si le code est faux.
     * La comparaison est faite en temps constant.
     */
    public static long verifier(byte[] secret, String saisi, long epochSecondes) {
        String c = saisi == null ? "" : saisi.replaceAll("[\\s-]", "");
        if (!c.matches("\\d{" + CHIFFRES + "}")) {
            return -1;
        }
        long courant = pas(epochSecondes);
        long trouve = -1;
        for (long p = courant - FENETRE; p <= courant + FENETRE; p++) {
            if (java.security.MessageDigest.isEqual(code(secret, p).getBytes(StandardCharsets.US_ASCII),
                    c.getBytes(StandardCharsets.US_ASCII))) {
                trouve = p;
            }
        }
        return trouve;
    }

    /** Lien d'enrôlement lu par les applications d'authentification (format « Key Uri »). */
    public static String uri(String emetteur, String compte, byte[] secret) {
        String libelle = enc(emetteur) + ":" + enc(compte);
        return "otpauth://totp/" + libelle + "?secret=" + base32(secret) + "&issuer=" + enc(emetteur)
                + "&algorithm=SHA1&digits=" + CHIFFRES + "&period=" + PAS_SECONDES;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    // ------------------------------------------------------------------ Base32

    public static String base32(byte[] donnees) {
        StringBuilder sb = new StringBuilder();
        int tampon = 0;
        int bits = 0;
        for (byte b : donnees) {
            tampon = (tampon << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                sb.append(BASE32.charAt((tampon >>> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            sb.append(BASE32.charAt((tampon << (5 - bits)) & 31));
        }
        return sb.toString();
    }

    public static byte[] depuisBase32(String texte) {
        String t = texte.replaceAll("[\\s=-]", "").toUpperCase(Locale.ROOT);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int tampon = 0;
        int bits = 0;
        for (char ch : t.toCharArray()) {
            int v = BASE32.indexOf(ch);
            if (v < 0) {
                throw new IllegalArgumentException("Caractère Base32 invalide : " + ch);
            }
            tampon = (tampon << 5) | v;
            bits += 5;
            if (bits >= 8) {
                out.write((tampon >>> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }

    /** Secret groupé par 4 pour la saisie manuelle : « JBSW Y3DP EHPK 3PXP ». */
    public static String lisible(byte[] secret) {
        return base32(secret).replaceAll("(.{4})(?!$)", "$1 ");
    }
}

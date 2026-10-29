package com.tdsi.memotheque.util;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Primitives cryptographiques communes (JDK uniquement, aucune dépendance) :
 * encodage non ambigu des champs, HMAC-SHA256, AES-256-GCM, SHA-256.
 */
public final class CryptoUtil {

    public static final int TAILLE_IV = 12;          // 96 bits, recommandé pour GCM
    public static final int TAILLE_TAG_BITS = 128;

    private static final SecureRandom ALEATOIRE = new SecureRandom();

    private CryptoUtil() { }

    /**
     * Encodage canonique « longueur:valeur » de chaque champ : deux listes différentes
     * ne peuvent pas produire les mêmes octets (pas d'ambiguïté avec un séparateur).
     * null est encodé « -1: » pour être distinct de la chaîne vide.
     */
    public static byte[] encoder(String... champs) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (String champ : champs) {
            if (champ == null) {
                out.writeBytes("-1:".getBytes(StandardCharsets.US_ASCII));
                continue;
            }
            byte[] valeur = champ.getBytes(StandardCharsets.UTF_8);
            out.writeBytes((valeur.length + ":").getBytes(StandardCharsets.US_ASCII));
            out.writeBytes(valeur);
        }
        return out.toByteArray();
    }

    public static String hmacHex(SecretKey cle, byte[] donnees) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(cle);
            return HexFormat.of().formatHex(mac.doFinal(donnees));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 indisponible", e);
        }
    }

    /** Comparaison en temps constant de deux chaînes (hex, Base64...). */
    public static boolean egal(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256Hex(byte[] donnees) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(donnees));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    /** AES-256-GCM : retourne IV (12 octets) || texte chiffré || tag (16 octets). */
    public static byte[] chiffrer(SecretKey cle, byte[] clair, byte[] donneesAssociees) {
        try {
            byte[] iv = new byte[TAILLE_IV];
            ALEATOIRE.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, aes(cle), new GCMParameterSpec(TAILLE_TAG_BITS, iv));
            if (donneesAssociees != null) {
                c.updateAAD(donneesAssociees);
            }
            byte[] chiffre = c.doFinal(clair);
            return ByteBuffer.allocate(iv.length + chiffre.length).put(iv).put(chiffre).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Chiffrement AES-GCM impossible", e);
        }
    }

    /**
     * Déchiffre le format de chiffrer(). Toute modification du texte chiffré, de l'IV
     * ou des données associées fait échouer le tag : SecurityException.
     */
    public static byte[] dechiffrer(SecretKey cle, byte[] ivEtChiffre, byte[] donneesAssociees) {
        if (ivEtChiffre == null || ivEtChiffre.length < TAILLE_IV + TAILLE_TAG_BITS / 8) {
            throw new SecurityException("Données chiffrées tronquées.");
        }
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, aes(cle), new GCMParameterSpec(TAILLE_TAG_BITS, ivEtChiffre, 0, TAILLE_IV));
            if (donneesAssociees != null) {
                c.updateAAD(donneesAssociees);
            }
            return c.doFinal(ivEtChiffre, TAILLE_IV, ivEtChiffre.length - TAILLE_IV);
        } catch (javax.crypto.AEADBadTagException e) {
            throw new SecurityException("Authentification AES-GCM échouée : données modifiées ou mauvaise clé.", e);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Déchiffrement AES-GCM impossible", e);
        }
    }

    /** Clé AES à partir d'octets bruts (clé dérivée d'un mot de passe, par exemple). */
    public static SecretKey cleAes(byte[] octets) {
        return new SecretKeySpec(octets, "AES");
    }

    private static SecretKey aes(SecretKey cle) {
        return "AES".equals(cle.getAlgorithm()) ? cle : new SecretKeySpec(cle.getEncoded(), "AES");
    }
}

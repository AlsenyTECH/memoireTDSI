package com.tdsi.memotheque.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * [M2] Hachage des mots de passe : PBKDF2WithHmacSHA256, sel aléatoire de 16 octets (SecureRandom),
 * encodage Base64, comparaison en temps constant (MessageDigest.isEqual).
 *
 * Format stocké : « pbkdf2-sha256$<itérations>$<hash Base64> ». Le nombre d'itérations voyage
 * avec le hash : on peut l'augmenter sans casser les comptes existants (besoinRehash).
 * Les anciens hashes sans préfixe (120 000 itérations) restent vérifiables.
 */
public final class PasswordUtil {

    /** Recommandation OWASP 2023 pour PBKDF2-HMAC-SHA256. */
    public static final int ITERATIONS_PAR_DEFAUT = 600_000;
    private static final int ITERATIONS_HISTORIQUES = 120_000;
    private static final int ITERATIONS_MINIMUM = 10_000;
    private static final String PREFIXE = "pbkdf2-sha256$";

    /** Les tests peuvent abaisser le coût avec -Dmemotdsi.pbkdf2.iterations (jamais sous 10 000). */
    public static final int ITERATIONS = lireIterations();

    private static final SecureRandom ALEATOIRE = new SecureRandom();
    private static final String ALPHABET_TEMPORAIRE = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    private PasswordUtil() { }

    /** Génère un sel aléatoire encodé en Base64. */
    public static String genererSel() {
        byte[] sel = new byte[16];
        ALEATOIRE.nextBytes(sel);
        return Base64.getEncoder().encodeToString(sel);
    }

    /** Retourne le hash du mot de passe avec le sel donné, au format versionné. */
    public static String hasher(String motDePasse, String sel) {
        byte[] hash = deriver(motDePasse.toCharArray(), Base64.getDecoder().decode(sel), ITERATIONS, 256);
        return PREFIXE + ITERATIONS + "$" + Base64.getEncoder().encodeToString(hash);
    }

    /** Vérifie un mot de passe saisi par rapport au hash et au sel enregistrés (temps constant). */
    public static boolean verifier(String motDePasse, String sel, String hashAttendu) {
        if (motDePasse == null || sel == null || hashAttendu == null) {
            return false;
        }
        try {
            int iterations = ITERATIONS_HISTORIQUES;
            String hashBase64 = hashAttendu;

            if (hashAttendu.startsWith(PREFIXE)) {
                String[] parties = hashAttendu.substring(PREFIXE.length()).split("\\$", 2);
                iterations = Integer.parseInt(parties[0]);
                hashBase64 = parties[1];
            }

            byte[] attendu = Base64.getDecoder().decode(hashBase64);
            byte[] calcule = deriver(motDePasse.toCharArray(), Base64.getDecoder().decode(sel),
                    iterations, attendu.length * 8);
            return MessageDigest.isEqual(calcule, attendu);

        } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
            return false;   // hash ou sel mal formé : on refuse
        }
    }

    /** Vrai si le hash a été calculé avec un ancien format ou moins d'itérations qu'aujourd'hui. */
    public static boolean besoinRehash(String hash) {
        if (hash == null || !hash.startsWith(PREFIXE)) {
            return true;
        }
        try {
            int iterations = Integer.parseInt(hash.substring(PREFIXE.length()).split("\\$", 2)[0]);
            return iterations < ITERATIONS;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    /** Mot de passe temporaire (réinitialisation par l'admin) : 14 caractères sans caractères ambigus. */
    public static String genererMotDePasseTemporaire() {
        StringBuilder sb = new StringBuilder();
        while (sb.length() < 14 || !Validator.motDePasseRobuste(sb.toString())) {
            if (sb.length() >= 14) {
                sb.setLength(0);
            }
            sb.append(ALPHABET_TEMPORAIRE.charAt(ALEATOIRE.nextInt(ALPHABET_TEMPORAIRE.length())));
        }
        return sb.toString();
    }

    /** PBKDF2-HMAC-SHA256 (aussi utilisé pour dériver des clés AES à partir d'un mot de passe). */
    public static byte[] deriver(char[] motDePasse, byte[] sel, int iterations, int bits) {
        PBEKeySpec spec = new PBEKeySpec(motDePasse, sel, iterations, bits);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Impossible de calculer le hachage du mot de passe.", e);
        } finally {
            spec.clearPassword();
        }
    }

    private static int lireIterations() {
        String valeur = System.getProperty("memotdsi.pbkdf2.iterations");
        if (valeur == null) {
            return ITERATIONS_PAR_DEFAUT;
        }
        try {
            return Math.max(ITERATIONS_MINIMUM, Integer.parseInt(valeur.trim()));
        } catch (NumberFormatException e) {
            return ITERATIONS_PAR_DEFAUT;
        }
    }
}

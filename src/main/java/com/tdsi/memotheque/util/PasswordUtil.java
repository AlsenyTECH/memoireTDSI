package com.tdsi.memotheque.util;
import java.security.SecureRandom;
import java.util.Base64;
import java.security.spec.InvalidKeySpecException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * [M2] Hachage des mots de passe.
 * À faire : PBKDF2WithHmacSHA256, 120 000 itérations, sel aléatoire de 16 octets (SecureRandom),
 * encodage Base64, comparaison en temps constant (MessageDigest.isEqual).
 */
public final class PasswordUtil {

    private PasswordUtil() { }

    /** Génère un sel aléatoire encodé en Base64. */
    public static String genererSel() {
        byte[] sel = new byte[16];
        new SecureRandom().nextBytes(sel);
        return Base64.getEncoder().encodeToString(sel);
    }

    /** Retourne le hash (Base64) du mot de passe avec le sel donné. */
    public static String hasher(String motDePasse, String sel) {
        byte[] selDecode = Base64.getDecoder().decode(sel);

        PBEKeySpec spec = new PBEKeySpec(
            motDePasse.toCharArray(),
            selDecode,
            120_000,
            256
        );

       try {
            SecretKeyFactory factory =
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");

            byte[] hash = factory.generateSecret(spec).getEncoded();

           return Base64.getEncoder().encodeToString(hash);

        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Impossible de calculer le hachage du mot de passe.", e);
        } finally {
            spec.clearPassword();
        }
    }

    /** Vérifie un mot de passe saisi par rapport au hash et au sel enregistrés. */
    public static boolean verifier(String motDePasse, String sel, String hashAttendu) {
        byte[] hashCalcule = Base64.getDecoder().decode(hasher(motDePasse, sel));
        byte[] hashEnregistre = Base64.getDecoder().decode(hashAttendu);

        return MessageDigest.isEqual(hashCalcule, hashEnregistre);
    }
}

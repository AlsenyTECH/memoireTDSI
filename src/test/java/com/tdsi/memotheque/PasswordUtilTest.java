package com.tdsi.memotheque;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.tdsi.memotheque.util.PasswordUtil;

/**
 * [M2] Tests du hachage des mots de passe (à écrire quand PasswordUtil sera
 * implémenté).
 */
class PasswordUtilTest {

    @Test
    void motDePasseCorrectEstVerifie() {
        String motDePasse = "MotDePasse123";
        String sel = PasswordUtil.genererSel();
        String hash = PasswordUtil.hasher(motDePasse, sel);
        boolean resultat = PasswordUtil.verifier(motDePasse, sel, hash);
        assertTrue(resultat);
    }

    @Test
    void mauvaisMotDePasseEstRefuse() {
        String motDePasse = "MotDePasse123";
        String mauvaisMotDePasse = "MauvaisMotDePasse";

        String sel = PasswordUtil.genererSel();

        String hash = PasswordUtil.hasher(motDePasse, sel);

        boolean resultat = PasswordUtil.verifier(mauvaisMotDePasse, sel, hash);

        assertFalse(resultat);
    }

    @Test
    void deuxSelsDifferentsProduisentDeuxHashDifferents() {
        String motDePasse = "MotDePasse123";

        String sel1 = PasswordUtil.genererSel();
        String sel2 = PasswordUtil.genererSel();

        String hash1 = PasswordUtil.hasher(motDePasse, sel1);
        String hash2 = PasswordUtil.hasher(motDePasse, sel2);

        assertNotEquals(hash1, hash2);
    }
}

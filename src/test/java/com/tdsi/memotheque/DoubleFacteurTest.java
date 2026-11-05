package com.tdsi.memotheque;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tdsi.memotheque.dao.impl.UtilisateurDAOImpl;
import com.tdsi.memotheque.model.Role;
import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.service.AuthService;
import com.tdsi.memotheque.service.DoubleFacteurService;
import com.tdsi.memotheque.util.SessionManager;
import com.tdsi.memotheque.util.Totp;

/** Double authentification TOTP : activation, connexion en deux étapes, anti-rejeu, verrouillage, retrait. */
class DoubleFacteurTest {

    @TempDir
    Path dossier;

    private Utilisateur admin;
    private DoubleFacteurService service;
    private AuthService auth;
    private byte[] secret;

    @BeforeEach
    void preparer() throws Exception {
        BaseDeTest.ouvrirBaseTemporaire(dossier);
        admin = BaseDeTest.creerUtilisateur("admin@test.sn", null, Role.ADMIN, null, true);
        service = new DoubleFacteurService();
        auth = new AuthService();
        SessionManager.ouvrir(admin);
        DoubleFacteurService.Enrolement e = service.preparer();
        secret = e.secret().clone();
        assertThrows(IllegalArgumentException.class, () -> service.activer(e, "000000x"));
        service.activer(e, code(0));
        assertTrue(service.estActive(admin.getId()));
        SessionManager.fermer();
    }

    @AfterEach
    void nettoyer() {
        BaseDeTest.fermer();
    }

    /** Code du pas courant + décalage (le pas courant a été consommé par l'activation). */
    private String code(int decalage) {
        return Totp.code(secret, Totp.pas(Instant.now().getEpochSecond()) + decalage);
    }

    @Test
    void connexionEnDeuxEtapes() throws Exception {
        assertThrows(AuthService.DeuxiemeFacteurRequis.class,
                () -> auth.connecter("admin@test.sn", BaseDeTest.MOT_DE_PASSE));
        assertFalse(SessionManager.estConnecte(), "aucune session sans le code");
        // Mauvais mot de passe : on ne révèle pas que le compte a un second facteur.
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> auth.connecter("admin@test.sn", "mauvais", null));
        assertInstanceOf(IllegalArgumentException.class, e);

        String c = code(1);
        assertNotNull(auth.connecter("admin@test.sn", BaseDeTest.MOT_DE_PASSE, c));
        assertTrue(SessionManager.estConnecte());
        SessionManager.fermer();
        assertThrows(IllegalArgumentException.class,
                () -> auth.connecter("admin@test.sn", BaseDeTest.MOT_DE_PASSE, c), "rejeu du même code");
        assertThrows(IllegalArgumentException.class,
                () -> auth.connecter("admin@test.sn", BaseDeTest.MOT_DE_PASSE, code(0)), "code plus ancien");
    }

    @Test
    void codesFauxVerrouillentLeCompte() throws Exception {
        for (int i = 0; i < 4; i++) {
            assertThrows(IllegalArgumentException.class,
                    () -> auth.connecter("admin@test.sn", BaseDeTest.MOT_DE_PASSE, "000000"));
        }
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> auth.connecter("admin@test.sn", BaseDeTest.MOT_DE_PASSE, "000000"));
        assertTrue(e.getMessage().contains("verrouillé"), e.getMessage());
        assertTrue(new UtilisateurDAOImpl().findById(admin.getId()).isVerrouille());
    }

    @Test
    void desactivationEtRetrait() throws Exception {
        Utilisateur etu = BaseDeTest.creerUtilisateur("e@test.sn", "E-1", Role.ETUDIANT, null, true);
        SessionManager.ouvrir(etu);
        assertThrows(SecurityException.class, () -> service.retirer(admin.getId()));

        SessionManager.ouvrir(admin);
        assertThrows(IllegalArgumentException.class, () -> service.desactiver("faux"));
        service.desactiver(BaseDeTest.MOT_DE_PASSE);
        assertFalse(service.estActive(admin.getId()));
        SessionManager.fermer();
        assertNotNull(auth.connecter("admin@test.sn", BaseDeTest.MOT_DE_PASSE));
    }

    @Test
    void secretChiffreEtLieAuCompte() throws Exception {
        var enr = new com.tdsi.memotheque.dao.impl.DoubleFacteurDAOImpl().lire(admin.getId());
        assertFalse(enr.secretChiffre().contains(Totp.base32(secret)));
        // Copie du secret chiffré sur un autre compte : le déchiffrement GCM échoue (données associées).
        Utilisateur autre = BaseDeTest.creerUtilisateur("autre@test.sn", null, Role.ADMIN, null, true);
        new com.tdsi.memotheque.dao.impl.DoubleFacteurDAOImpl().enregistrer(autre.getId(), enr.secretChiffre());
        assertThrows(SecurityException.class,
                () -> auth.connecter("autre@test.sn", BaseDeTest.MOT_DE_PASSE, code(1)));
    }
}

package com.tdsi.memotheque;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tdsi.memotheque.model.Role;
import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.service.AuthService;
import com.tdsi.memotheque.util.Donnees;
import com.tdsi.memotheque.util.TacheFond;

/** Installation neuve : création du premier administrateur, dossier de données. */
class PremierDemarrageTest {

    @TempDir
    Path dossier;

    private AuthService auth;

    @BeforeEach
    void preparer() throws Exception {
        BaseDeTest.ouvrirBaseTemporaire(dossier);
        auth = new AuthService();
    }

    @AfterEach
    void nettoyer() {
        BaseDeTest.fermer();
        System.clearProperty(Donnees.PROPRIETE);
    }

    @Test
    void premierAdminPuisPlusJamais() throws Exception {
        assertFalse(auth.adminExiste());

        Utilisateur admin = auth.creerPremierAdmin("Diallo", "Alseny", "Admin@TDSI.sn", "AdminTDSI2026x");
        assertEquals(Role.ADMIN, admin.getRole());
        assertTrue(auth.adminExiste());
        assertNotNull(auth.connecter("admin@tdsi.sn", "AdminTDSI2026x"));

        assertThrows(SecurityException.class,
                () -> auth.creerPremierAdmin("X", "Y", "pirate@tdsi.sn", "PirateTDSI2026x"),
                "impossible de créer un second admin par ce chemin");
    }

    @Test
    void premierAdminExigeUnMotDePasseFort() {
        assertThrows(IllegalArgumentException.class,
                () -> auth.creerPremierAdmin("Diallo", "Alseny", "admin@tdsi.sn", "Court12"));
        assertThrows(IllegalArgumentException.class,
                () -> auth.creerPremierAdmin("Diallo", "Alseny", "pas-un-email", "AdminTDSI2026x"));
    }

    @Test
    void dossierDeDonneesConfigurable() {
        System.clearProperty(Donnees.PROPRIETE);
        assertEquals(Path.of("memotdsi.db"), Donnees.chemin("memotdsi.db"));

        System.setProperty(Donnees.PROPRIETE, dossier.toString());
        assertEquals(dossier.resolve("storage"), Donnees.chemin("storage"));
    }

    @Test
    void messagesDErreurDesTachesDeFond() {
        assertEquals("Motif obligatoire", TacheFond.message(new IllegalArgumentException("Motif obligatoire")));
        assertEquals("Erreur technique : disque plein", TacheFond.message(new java.io.IOException("disque plein")));
    }
}

package com.tdsi.memotheque;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tdsi.memotheque.dao.impl.JournalDAOImpl;
import com.tdsi.memotheque.model.JournalActivite;
import com.tdsi.memotheque.model.Niveau;
import com.tdsi.memotheque.model.Role;
import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.service.JournalService;
import com.tdsi.memotheque.util.SessionManager;

/** [M6] Tests de JournalDAOImpl et JournalService (base temporaire). */
class JournalTest {

    @TempDir
    Path dossier;

    private JournalService service;
    private Utilisateur admin;
    private Utilisateur etudiant;

    @BeforeEach
    void preparer() throws Exception {
        BaseDeTest.ouvrirBaseTemporaire(dossier);
        service = new JournalService();
        admin = BaseDeTest.creerUtilisateur("admin@test.sn", null, Role.ADMIN, null, true);
        etudiant = BaseDeTest.creerUtilisateur("etu@test.sn", "ETU-0001", Role.ETUDIANT, Niveau.L1, true);
    }

    @AfterEach
    void nettoyer() {
        BaseDeTest.fermer();
    }

    @Test
    void logAvecEtSansUtilisateurPuisLecture() throws Exception {
        service.log(null, "ECHEC_CONNEXION", "identifiant inconnu");
        SessionManager.ouvrir(etudiant);
        service.log("DEPOT", "test");

        SessionManager.ouvrir(admin);
        List<JournalActivite> lignes = service.derniers(10);

        assertEquals(2, lignes.size());
        assertEquals("DEPOT", lignes.get(0).getAction());           // plus récent d'abord
        assertEquals(etudiant.getId(), lignes.get(0).getIdUtilisateur());
        assertNull(lignes.get(1).getIdUtilisateur());
        assertNotNull(lignes.get(1).getDateHeure());
    }

    @Test
    void lectureReserveeALadmin() {
        assertThrows(SecurityException.class, () -> service.derniers(10));
        SessionManager.ouvrir(etudiant);
        assertThrows(SecurityException.class, () -> service.derniers(10));
    }

    @Test
    void limiteDuDao() throws Exception {
        JournalDAOImpl dao = new JournalDAOImpl();
        for (int i = 0; i < 5; i++) {
            service.log(null, "TEST", "ligne " + i);
        }
        assertEquals(3, dao.derniers(3).size());
        assertThrows(IllegalArgumentException.class, () -> dao.derniers(0));
    }

    @Test
    void injectionDansLesDetailsStockeeTelleQuelle() throws Exception {
        service.log(null, "TEST", "x'); DELETE FROM journal_activite; --");
        SessionManager.ouvrir(admin);
        assertEquals("x'); DELETE FROM journal_activite; --", service.derniers(1).get(0).getDetails());
    }
}

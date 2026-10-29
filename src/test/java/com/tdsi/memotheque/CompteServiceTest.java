package com.tdsi.memotheque;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tdsi.memotheque.dao.impl.UtilisateurDAOImpl;
import com.tdsi.memotheque.model.Niveau;
import com.tdsi.memotheque.model.Role;
import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.service.AuthService;
import com.tdsi.memotheque.service.CompteService;
import com.tdsi.memotheque.util.PasswordUtil;
import com.tdsi.memotheque.util.SessionManager;

/** [M6] Tests de CompteService (base temporaire). */
class CompteServiceTest {

    @TempDir
    Path dossier;

    private CompteService service;
    private UtilisateurDAOImpl dao;
    private Utilisateur admin;
    private Utilisateur etudiant;

    @BeforeEach
    void preparer() throws Exception {
        BaseDeTest.ouvrirBaseTemporaire(dossier);
        service = new CompteService();
        dao = new UtilisateurDAOImpl();
        admin = BaseDeTest.creerUtilisateur("admin@test.sn", null, Role.ADMIN, null, true);
        etudiant = BaseDeTest.creerUtilisateur("etu@test.sn", "ETU-0001", Role.ETUDIANT, Niveau.L3, false);
    }

    @AfterEach
    void nettoyer() {
        BaseDeTest.fermer();
    }

    @Test
    void ladminActiveUnCompteInscritQuiPeutAlorsSeConnecter() throws Exception {
        AuthService auth = new AuthService();
        assertThrows(IllegalArgumentException.class,
                () -> auth.connecter("etu@test.sn", BaseDeTest.MOT_DE_PASSE));

        SessionManager.ouvrir(admin);
        service.activer(etudiant.getId());
        SessionManager.fermer();

        assertTrue(dao.findById(etudiant.getId()).isActif());
        assertEquals(1, Journal.compter("ACTIVATION_COMPTE"));
        assertNotNull(auth.connecter("etu@test.sn", BaseDeTest.MOT_DE_PASSE));
    }

    @Test
    void unEtudiantNePeutPasGererLesComptes() throws Exception {
        Utilisateur actif = BaseDeTest.creerUtilisateur("e2@test.sn", "ETU-0002",
                Role.ETUDIANT, Niveau.L3, true);
        SessionManager.ouvrir(actif);

        assertThrows(SecurityException.class, () -> service.listerComptes());
        assertThrows(SecurityException.class, () -> service.activer(etudiant.getId()));
        assertThrows(SecurityException.class, () -> service.desactiver(admin.getId()));
        assertThrows(SecurityException.class, () -> service.deverrouiller(etudiant.getId()));
        assertThrows(SecurityException.class,
                () -> service.creerEncadreur("Diop", "Awa", "awa@test.sn", "MotDePasse1"));
        assertFalse(dao.findById(etudiant.getId()).isActif());
    }

    @Test
    void unEncadreurNePeutPasGererLesComptes() throws Exception {
        SessionManager.ouvrir(BaseDeTest.creerUtilisateur("enc@test.sn", null, Role.ENCADREUR, null, true));
        assertThrows(SecurityException.class, () -> service.activer(etudiant.getId()));
    }

    @Test
    void sansConnexionRefuse() {
        assertThrows(SecurityException.class, () -> service.listerComptes());
    }

    @Test
    void desactiverMaisPasSoiMeme() throws Exception {
        SessionManager.ouvrir(admin);
        service.activer(etudiant.getId());
        service.desactiver(etudiant.getId());

        assertFalse(dao.findById(etudiant.getId()).isActif());
        assertThrows(IllegalArgumentException.class, () -> service.desactiver(admin.getId()));
        assertTrue(dao.findById(admin.getId()).isActif());
    }

    @Test
    void deverrouillerRemetLeCompteurAZero() throws Exception {
        dao.mettreAJourTentatives(etudiant.getId(), 5, true);

        SessionManager.ouvrir(admin);
        service.deverrouiller(etudiant.getId());

        Utilisateur lu = dao.findById(etudiant.getId());
        assertFalse(lu.isVerrouille());
        assertEquals(0, lu.getTentativesEchec());
        assertEquals(1, Journal.compter("DEVERROUILLAGE_COMPTE"));
    }

    @Test
    void creerEncadreurActifAvecMotDePasseHache() throws Exception {
        SessionManager.ouvrir(admin);
        Utilisateur u = service.creerEncadreur(" Diop ", "Awa", "Awa.Diop@Test.sn", "MotDePasse1");

        Utilisateur lu = dao.findById(u.getId());
        assertEquals(Role.ENCADREUR, lu.getRole());
        assertTrue(lu.isActif());
        assertEquals("awa.diop@test.sn", lu.getEmail());
        assertEquals("Diop", lu.getNom());
        assertNotEquals("MotDePasse1", lu.getMotDePasseHash());
        assertTrue(PasswordUtil.verifier("MotDePasse1", lu.getSel(), lu.getMotDePasseHash()));
        assertEquals(1, Journal.compter("CREATION_COMPTE"));

        assertThrows(IllegalArgumentException.class,
                () -> service.creerEncadreur("X", "Y", "awa.diop@test.sn", "MotDePasse1"));
        assertThrows(IllegalArgumentException.class,
                () -> service.creerEncadreur("X", "Y", "pas-un-email", "MotDePasse1"));
        assertThrows(IllegalArgumentException.class,
                () -> service.creerEncadreur("X", "Y", "court@test.sn", "court"));
    }

    @Test
    void changerSonMotDePasse() throws Exception {
        Utilisateur actif = BaseDeTest.creerUtilisateur("e2@test.sn", "ETU-0002",
                Role.ETUDIANT, Niveau.L3, true);
        SessionManager.ouvrir(actif);

        assertThrows(IllegalArgumentException.class,
                () -> service.changerMotDePasse("mauvais-mdp", "NouveauMdp456"));
        assertThrows(IllegalArgumentException.class,
                () -> service.changerMotDePasse(BaseDeTest.MOT_DE_PASSE, "court"));
        assertThrows(IllegalArgumentException.class,
                () -> service.changerMotDePasse(BaseDeTest.MOT_DE_PASSE, BaseDeTest.MOT_DE_PASSE));

        service.changerMotDePasse(BaseDeTest.MOT_DE_PASSE, "NouveauMdp456");
        SessionManager.fermer();

        AuthService auth = new AuthService();
        assertThrows(IllegalArgumentException.class,
                () -> auth.connecter("e2@test.sn", BaseDeTest.MOT_DE_PASSE));
        assertNotNull(auth.connecter("e2@test.sn", "NouveauMdp456"));
        assertEquals(1, Journal.compter("CHANGEMENT_MOT_DE_PASSE"));
    }
}

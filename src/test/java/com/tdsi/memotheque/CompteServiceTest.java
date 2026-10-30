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
                () -> service.creerEncadreur("Diop", "Awa", "awa@test.sn", "Encadrement2026"));
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
        Utilisateur u = service.creerEncadreur(" Diop ", "Awa", "Awa.Diop@Test.sn", "Encadrement2026");

        Utilisateur lu = dao.findById(u.getId());
        assertEquals(Role.ENCADREUR, lu.getRole());
        assertTrue(lu.isActif());
        assertEquals("awa.diop@test.sn", lu.getEmail());
        assertEquals("Diop", lu.getNom());
        assertNotEquals("Encadrement2026", lu.getMotDePasseHash());
        assertTrue(PasswordUtil.verifier("Encadrement2026", lu.getSel(), lu.getMotDePasseHash()));
        assertEquals(1, Journal.compter("CREATION_COMPTE"));

        assertThrows(IllegalArgumentException.class,
                () -> service.creerEncadreur("X", "Y", "awa.diop@test.sn", "Encadrement2026"));
        assertThrows(IllegalArgumentException.class,
                () -> service.creerEncadreur("X", "Y", "pas-un-email", "Encadrement2026"));
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

    @Test
    void importCsvDesEtudiantsParLaScolarite() throws Exception {
        SessionManager.ouvrir(admin);
        java.util.List<CompteService.LigneImport> r = service.importerEtudiants(java.util.List.of(
                "\uFEFFmatricule;nom;prenom;email;niveau",
                "ETU-0100;Sow;Awa;awa.sow@test.sn;M1",
                "ETU-0101;Ba;Moussa;MOUSSA.BA@test.sn;l3",
                "ETU-0001;NomETUDIANT;Prenom;etu@test.sn;M2",
                "ETU-0102;Fall;Ibou;pas-un-email;L1",
                "ETU-0103;Diop;Fatou;fatou@test.sn;M3",
                "ETU-0104;Ka;Ali;awa.sow@test.sn;L1",
                "ETU-0105;Sy;Omar;admin@test.sn;L2",
                "ligne;incomplete"));

        assertEquals(8, r.size());
        assertEquals("CREE", r.get(0).resultat());
        assertEquals("CREE", r.get(1).resultat());
        assertEquals("REINSCRIT", r.get(2).resultat(), "même matricule et même e-mail : niveau mis à jour");
        for (int i = 3; i < 8; i++) {
            assertEquals("ERREUR", r.get(i).resultat(), r.get(i).toString());
        }

        Utilisateur awa = dao.findByEmailOuMatricule("awa.sow@test.sn");
        assertEquals(Niveau.M1, awa.getNiveau());
        assertTrue(awa.isActif() && awa.isDoitChangerMotDePasse());
        assertNotNull(new AuthService().connecter("awa.sow@test.sn", r.get(0).detail()), "mot de passe temporaire");
        assertEquals(Niveau.L3, dao.findByEmailOuMatricule("moussa.ba@test.sn").getNiveau());
        assertEquals(Niveau.M2, dao.findById(etudiant.getId()).getNiveau());
        assertEquals(Role.ADMIN, dao.findByEmailOuMatricule("admin@test.sn").getRole(), "un admin n'est jamais écrasé");
        assertEquals(1, Journal.compter("IMPORT_ETUDIANTS"));

        SessionManager.ouvrir(etudiant);
        assertThrows(SecurityException.class, () -> service.importerEtudiants(java.util.List.of()));
    }
}

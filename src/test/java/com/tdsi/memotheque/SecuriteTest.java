package com.tdsi.memotheque;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tdsi.memotheque.dao.impl.MemoireDAOImpl;
import com.tdsi.memotheque.dao.impl.UtilisateurDAOImpl;
import com.tdsi.memotheque.dao.impl.ValidationDAOImpl;
import com.tdsi.memotheque.model.Memoire;
import com.tdsi.memotheque.model.MemoireCritere;
import com.tdsi.memotheque.model.Niveau;
import com.tdsi.memotheque.model.Role;
import com.tdsi.memotheque.model.StatutMemoire;
import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.service.AuthService;
import com.tdsi.memotheque.service.CompteService;
import com.tdsi.memotheque.service.RechercheService;
import com.tdsi.memotheque.service.ValidationService;
import com.tdsi.memotheque.util.SessionManager;

/**
 * Tests de sécurité de bout en bout (vrais DAO, base SQLite temporaire) :
 * injection SQL, verrouillage après 5 échecs, contrôle des rôles, refus sans motif.
 */
class SecuriteTest {

    private static final String INJECTION = "' OR '1'='1";

    @TempDir
    Path dossier;

    private AuthService auth;
    private UtilisateurDAOImpl utilisateurDAO;
    private Utilisateur etudiant;
    private Utilisateur encadreur;
    private Memoire memoire;

    @BeforeEach
    void preparer() throws Exception {
        BaseDeTest.ouvrirBaseTemporaire(dossier);
        auth = new AuthService();
        utilisateurDAO = new UtilisateurDAOImpl();
        etudiant = BaseDeTest.creerUtilisateur("etu@test.sn", "ETU-0001", Role.ETUDIANT, Niveau.M2, true);
        encadreur = BaseDeTest.creerUtilisateur("enc@test.sn", null, Role.ENCADREUR, null, true);
        memoire = BaseDeTest.deposer(dossier, etudiant, encadreur, "2024-2025", Niveau.M2);
    }

    @AfterEach
    void nettoyer() {
        BaseDeTest.fermer();
    }

    @Test
    void injectionSqlALaConnexionSansEffet() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> auth.connecter(INJECTION, INJECTION));
        assertThrows(IllegalArgumentException.class, () -> auth.connecter("etu@test.sn", INJECTION));
        assertThrows(IllegalArgumentException.class,
                () -> auth.connecter("etu@test.sn' --", BaseDeTest.MOT_DE_PASSE));
        assertFalse(SessionManager.estConnecte());
        assertEquals(2, utilisateurDAO.findAll().size(), "la table utilisateur est intacte");
    }

    @Test
    void injectionSqlDansLaRechercheSansEffet() throws Exception {
        new ValidationDAOImpl().valider(memoire.getId(), encadreur.getId());
        RechercheService recherche = new RechercheService();

        MemoireCritere normal = new MemoireCritere();
        assertEquals(1, recherche.compterPublic(normal));

        MemoireCritere injection = new MemoireCritere();
        injection.setMotCle(INJECTION);
        assertEquals(0, recherche.compterPublic(injection));
        assertTrue(recherche.rechercherPublic(injection).isEmpty());

        MemoireCritere annee = new MemoireCritere();
        annee.setAnneeAcademique("2024-2025' OR '1'='1");
        assertEquals(0, recherche.compterPublic(annee));
    }

    @Test
    void laRechercheNeMontrePasLesMemoiresEnAttente() throws Exception {
        MemoireCritere critere = new MemoireCritere();
        critere.setStatut(StatutMemoire.EN_ATTENTE);   // tentative de contournement
        assertEquals(0, new RechercheService().compterPublic(critere));
    }

    @Test
    void cinqMauvaisMotsDePasseBloquentLeCompte() throws Exception {
        for (int i = 1; i <= 4; i++) {
            assertThrows(IllegalArgumentException.class, () -> auth.connecter("etu@test.sn", "mauvais-mdp"));
            assertFalse(utilisateurDAO.findById(etudiant.getId()).isVerrouille());
        }
        IllegalArgumentException cinquieme = assertThrows(IllegalArgumentException.class,
                () -> auth.connecter("etu@test.sn", "mauvais-mdp"));
        assertTrue(cinquieme.getMessage().contains("verrouillé"));

        Utilisateur lu = utilisateurDAO.findById(etudiant.getId());
        assertTrue(lu.isVerrouille());
        assertEquals(5, lu.getTentativesEchec());

        // Même le bon mot de passe est refusé tant que l'admin n'a pas déverrouillé.
        assertThrows(IllegalArgumentException.class,
                () -> auth.connecter("etu@test.sn", BaseDeTest.MOT_DE_PASSE));
        assertFalse(SessionManager.estConnecte());

        Utilisateur admin = BaseDeTest.creerUtilisateur("admin@test.sn", null, Role.ADMIN, null, true);
        SessionManager.ouvrir(admin);
        new CompteService().deverrouiller(etudiant.getId());
        SessionManager.fermer();

        assertNotNull(auth.connecter("etu@test.sn", BaseDeTest.MOT_DE_PASSE));
    }

    @Test
    void compteInscritInactifNePeutPasSeConnecter() throws Exception {
        auth.inscrire("TDSI-2026-01", "Ndiaye", "Fatou", "fatou@test.sn", "MotDePasse99", Niveau.L1);

        Utilisateur cree = utilisateurDAO.findByEmailOuMatricule("fatou@test.sn");
        assertFalse(cree.isActif());
        assertNotEquals("MotDePasse99", cree.getMotDePasseHash());
        assertThrows(IllegalArgumentException.class, () -> auth.connecter("fatou@test.sn", "MotDePasse99"));
    }

    @Test
    void unEtudiantConnecteNePeutNiValiderNiGererLesComptes() throws Exception {
        Utilisateur connecte = auth.connecter("etu@test.sn", BaseDeTest.MOT_DE_PASSE);
        assertEquals(Role.ETUDIANT, connecte.getRole());

        assertThrows(SecurityException.class, () -> new ValidationService().valider(memoire.getId()));
        assertThrows(SecurityException.class, () -> new CompteService().listerComptes());
        assertThrows(SecurityException.class, () -> new CompteService().activer(etudiant.getId()));
        assertEquals(StatutMemoire.EN_ATTENTE, new MemoireDAOImpl().findById(memoire.getId()).getStatut());
    }

    @Test
    void refusSansMotifRejete() throws Exception {
        auth.connecter("enc@test.sn", BaseDeTest.MOT_DE_PASSE);
        ValidationService validation = new ValidationService();

        assertThrows(IllegalArgumentException.class, () -> validation.refuser(memoire.getId(), ""));
        assertEquals(StatutMemoire.EN_ATTENTE, new MemoireDAOImpl().findById(memoire.getId()).getStatut());
    }

    @Test
    void lePdfEstStockeSousUnNomUuidEtPasSousLeNomDOrigine() throws Exception {
        Path stocke = Path.of(memoire.getCheminFichier());
        assertTrue(Files.exists(stocke));
        assertFalse(stocke.getFileName().toString().startsWith("depot-"));
        assertTrue(memoire.getNomOriginal().startsWith("depot-"));
    }
}

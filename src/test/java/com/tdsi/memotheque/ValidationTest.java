package com.tdsi.memotheque;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tdsi.memotheque.dao.impl.MemoireDAOImpl;
import com.tdsi.memotheque.dao.impl.ValidationDAOImpl;
import com.tdsi.memotheque.model.Memoire;
import com.tdsi.memotheque.model.MemoireCritere;
import com.tdsi.memotheque.model.Niveau;
import com.tdsi.memotheque.model.Role;
import com.tdsi.memotheque.model.StatutMemoire;
import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.service.RechercheService;
import com.tdsi.memotheque.service.ValidationService;
import com.tdsi.memotheque.util.SessionManager;

/** [M5] Tests de ValidationDAOImpl et ValidationService (base temporaire). */
class ValidationTest {

    @TempDir
    Path dossier;

    private ValidationDAOImpl validationDAO;
    private MemoireDAOImpl memoireDAO;
    private ValidationService service;
    private Utilisateur etudiant;
    private Utilisateur encadreur;
    private Utilisateur autreEncadreur;
    private Utilisateur admin;
    private Memoire memoire;

    @BeforeEach
    void preparer() throws Exception {
        BaseDeTest.ouvrirBaseTemporaire(dossier);
        validationDAO = new ValidationDAOImpl();
        memoireDAO = new MemoireDAOImpl();
        service = new ValidationService();

        etudiant = BaseDeTest.creerUtilisateur("etu@test.sn", "ETU-0001", Role.ETUDIANT, Niveau.M2, true);
        encadreur = BaseDeTest.creerUtilisateur("enc@test.sn", null, Role.ENCADREUR, null, true);
        autreEncadreur = BaseDeTest.creerUtilisateur("enc2@test.sn", null, Role.ENCADREUR, null, true);
        admin = BaseDeTest.creerUtilisateur("admin@test.sn", null, Role.ADMIN, null, true);

        memoire = BaseDeTest.deposer(dossier, etudiant, encadreur, "2024-2025", Niveau.M2);
    }

    @AfterEach
    void nettoyer() {
        BaseDeTest.fermer();
    }

    // ------------------------------------------------------------------ DAO

    @Test
    void daoFindEnAttenteFiltreParEncadreur() throws Exception {
        Utilisateur etu2 = BaseDeTest.creerUtilisateur("etu2@test.sn", "ETU-0002",
                Role.ETUDIANT, Niveau.L3, true);
        BaseDeTest.deposer(dossier, etu2, autreEncadreur, "2024-2025", Niveau.L3);

        assertEquals(2, validationDAO.findEnAttente(null).size());
        List<Memoire> siens = validationDAO.findEnAttente(encadreur.getId());
        assertEquals(1, siens.size());
        assertEquals(memoire.getId(), siens.get(0).getId());
    }

    @Test
    void daoValiderRenseigneDatePublicationEtValidateur() throws Exception {
        validationDAO.valider(memoire.getId(), encadreur.getId());

        Memoire lu = memoireDAO.findById(memoire.getId());
        assertEquals(StatutMemoire.VALIDE, lu.getStatut());
        assertEquals(LocalDate.now(), lu.getDatePublication());
        assertEquals(encadreur.getId(), lu.getValidateur().getId());
        assertTrue(validationDAO.findEnAttente(null).isEmpty());
    }

    @Test
    void daoNeRetraitePasUnMemoireDejaTraite() throws Exception {
        validationDAO.valider(memoire.getId(), encadreur.getId());
        assertThrows(SQLException.class,
                () -> validationDAO.refuser(memoire.getId(), encadreur.getId(), "trop tard"));
        assertEquals(StatutMemoire.VALIDE, memoireDAO.findById(memoire.getId()).getStatut());
    }

    // ------------------------------------------------------------------ Service

    @Test
    void avisFavorableAutoriseLaSoutenanceSansPublier() throws Exception {
        RechercheService recherche = new RechercheService();

        SessionManager.ouvrir(encadreur);
        assertEquals(1, service.listerEnAttente().size());
        service.donnerAvisFavorable(memoire.getId());

        Memoire lu = memoireDAO.findById(memoire.getId());
        assertEquals(StatutMemoire.AUTORISE, lu.getStatut());
        assertEquals(LocalDate.now(), lu.getDateAvis());
        assertNull(lu.getDatePublication(), "pas encore publié : il faut d'abord soutenir");
        assertNull(lu.getMention(), "la mention vient du jury, pas de l'encadreur");
        assertEquals(0, recherche.compterPublic(new MemoireCritere()), "absent du catalogue avant soutenance");
        assertEquals(1, Journal.compter("AVIS_FAVORABLE"));
        assertTrue(service.listerEnAttente().isEmpty());
    }

    @Test
    void suiviParEncadreurEtParLaScolarite() throws Exception {
        SessionManager.ouvrir(encadreur);
        assertEquals(1, service.suivi().size());
        service.donnerAvisFavorable(memoire.getId());
        assertEquals(1, service.suivi().size(), "le mémoire autorisé reste suivi");

        SessionManager.ouvrir(autreEncadreur);
        assertTrue(service.suivi().isEmpty(), "on ne suit que ses propres encadrements");
        SessionManager.ouvrir(admin);
        assertEquals(1, service.suivi().size());
        SessionManager.ouvrir(etudiant);
        assertThrows(SecurityException.class, () -> service.suivi());
    }

    @Test
    void unEtudiantNePeutPasValiderNiRefuser() throws Exception {
        SessionManager.ouvrir(etudiant);

        assertThrows(SecurityException.class, () -> service.donnerAvisFavorable(memoire.getId()));
        assertThrows(SecurityException.class,
                () -> service.refuser(memoire.getId(), "Motif quelconque"));
        assertThrows(SecurityException.class, () -> service.listerEnAttente());
        assertEquals(StatutMemoire.EN_ATTENTE, memoireDAO.findById(memoire.getId()).getStatut());
    }

    @Test
    void sansConnexionAucuneValidation() {
        assertThrows(SecurityException.class, () -> service.donnerAvisFavorable(memoire.getId()));
    }

    @Test
    void unEncadreurNeValidePasLesMemoiresDesAutres() throws Exception {
        SessionManager.ouvrir(autreEncadreur);

        assertTrue(service.listerEnAttente().isEmpty());
        assertThrows(SecurityException.class, () -> service.donnerAvisFavorable(memoire.getId()));
        assertEquals(StatutMemoire.EN_ATTENTE, memoireDAO.findById(memoire.getId()).getStatut());
    }

    @Test
    void ladminPeutToutValider() throws Exception {
        SessionManager.ouvrir(admin);
        assertEquals(1, service.listerEnAttente().size());
        service.donnerAvisFavorable(memoire.getId());
        assertEquals(StatutMemoire.AUTORISE, memoireDAO.findById(memoire.getId()).getStatut());
    }

    @Test
    void refusSansMotifRejete() throws Exception {
        SessionManager.ouvrir(encadreur);

        assertThrows(IllegalArgumentException.class, () -> service.refuser(memoire.getId(), null));
        assertThrows(IllegalArgumentException.class, () -> service.refuser(memoire.getId(), "   "));
        assertThrows(IllegalArgumentException.class, () -> service.refuser(memoire.getId(), "abc"));
        assertEquals(StatutMemoire.EN_ATTENTE, memoireDAO.findById(memoire.getId()).getStatut());
        assertEquals(0, Journal.compter("REFUS"));
    }

    @Test
    void refusAvecMotifEnregistre() throws Exception {
        SessionManager.ouvrir(encadreur);
        service.refuser(memoire.getId(), "  La bibliographie est incomplète.  ");

        Memoire lu = memoireDAO.findById(memoire.getId());
        assertEquals(StatutMemoire.REFUSE, lu.getStatut());
        assertEquals("La bibliographie est incomplète.", lu.getMotifRefus());
        assertNull(lu.getDatePublication());
        assertEquals(1, Journal.compter("REFUS"));

        // Un mémoire déjà traité ne peut plus être validé.
        assertThrows(IllegalStateException.class, () -> service.donnerAvisFavorable(memoire.getId()));
    }

    @Test
    void pdfModifieApresDepotNePeutPasEtreValide() throws Exception {
        Files.writeString(Path.of(memoire.getCheminFichier()), "\n% ajout pirate",
                StandardOpenOption.APPEND);

        SessionManager.ouvrir(encadreur);
        assertThrows(SecurityException.class, () -> service.donnerAvisFavorable(memoire.getId()));
        assertThrows(SecurityException.class, () -> service.preparerLecture(memoire.getId()));
        assertEquals(StatutMemoire.EN_ATTENTE, memoireDAO.findById(memoire.getId()).getStatut());
    }
}

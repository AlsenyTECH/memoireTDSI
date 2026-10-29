package com.tdsi.memotheque;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tdsi.memotheque.dao.impl.DomaineDAOImpl;
import com.tdsi.memotheque.dao.impl.MemoireDAOImpl;
import com.tdsi.memotheque.dao.impl.UtilisateurDAOImpl;
import com.tdsi.memotheque.model.Domaine;
import com.tdsi.memotheque.model.Memoire;
import com.tdsi.memotheque.model.Niveau;
import com.tdsi.memotheque.model.Role;
import com.tdsi.memotheque.model.StatutMemoire;
import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.service.FichierService;
import com.tdsi.memotheque.service.JournalService;
import com.tdsi.memotheque.service.MemoireService;
import com.tdsi.memotheque.util.SessionManager;

/** [M3] Tests des règles de dépôt / modification / suppression (base temporaire). */
class MemoireServiceTest {

    @TempDir
    Path dossier;

    private MemoireService service;
    private MemoireDAOImpl memoireDAO;
    private Utilisateur etudiant;
    private Utilisateur encadreur;
    private Domaine domaine;
    private Path pdf;
    private Path storage;

    @BeforeEach
    void preparer() throws Exception {
        BaseDeTest.ouvrirBaseTemporaire(dossier);
        memoireDAO = new MemoireDAOImpl();
        storage = dossier.resolve("storage");
        service = new MemoireService(memoireDAO, new DomaineDAOImpl(), new UtilisateurDAOImpl(),
                new FichierService(storage), new JournalService());

        etudiant = BaseDeTest.creerUtilisateur("etu@test.sn", "ETU-0001", Role.ETUDIANT, Niveau.M2, true);
        encadreur = BaseDeTest.creerUtilisateur("enc@test.sn", null, Role.ENCADREUR, null, true);
        domaine = BaseDeTest.premierDomaine();
        pdf = BaseDeTest.creerPdf(dossier, "memoire.pdf");
    }

    @AfterEach
    void nettoyer() {
        BaseDeTest.fermer();
    }

    private Memoire valide() {
        return BaseDeTest.memoireValide(domaine, encadreur, "2024-2025", Niveau.M2);
    }

    @Test
    void depotReussiParUnEtudiant() throws Exception {
        SessionManager.ouvrir(etudiant);

        Memoire m = service.deposer(valide(), pdf);

        Memoire lu = memoireDAO.findById(m.getId());
        assertEquals(StatutMemoire.EN_ATTENTE, lu.getStatut());
        assertEquals(etudiant.getId(), lu.getAuteur().getId());
        assertEquals(LocalDate.now(), lu.getDateDepot());
        assertNull(lu.getDatePublication());
        assertTrue(Files.exists(Path.of(lu.getCheminFichier())));
        assertEquals(64, lu.getEmpreinteSha256().length());
        assertEquals(1, Journal.compter("DEPOT"));
    }

    @Test
    void auteurImposeParLaSessionEtPasParLeFormulaire() throws Exception {
        Utilisateur autre = BaseDeTest.creerUtilisateur("x@test.sn", "ETU-0009",
                Role.ETUDIANT, Niveau.L1, true);
        SessionManager.ouvrir(etudiant);

        Memoire m = valide();
        m.setAuteur(autre);
        m.setStatut(StatutMemoire.VALIDE);
        service.deposer(m, pdf);

        Memoire lu = memoireDAO.findById(m.getId());
        assertEquals(etudiant.getId(), lu.getAuteur().getId());
        assertEquals(StatutMemoire.EN_ATTENTE, lu.getStatut());
    }

    @Test
    void depotRefuseSansConnexionOuPourUnAutreRole() throws Exception {
        assertThrows(SecurityException.class, () -> service.deposer(valide(), pdf));

        SessionManager.ouvrir(encadreur);
        assertThrows(SecurityException.class, () -> service.deposer(valide(), pdf));
        assertTrue(memoireDAO.findAll().isEmpty());
    }

    @Test
    void unSeulMemoireParNiveauEtParAnnee() throws Exception {
        SessionManager.ouvrir(etudiant);
        service.deposer(valide(), pdf);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.deposer(valide(), pdf));
        assertTrue(e.getMessage().contains("déjà"));
        assertEquals(1, memoireDAO.findAll().size());
    }

    @Test
    void reglesDeValidationDesChamps() throws Exception {
        SessionManager.ouvrir(etudiant);

        Memoire titreCourt = valide();
        titreCourt.setTitre("abc");
        assertThrows(IllegalArgumentException.class, () -> service.deposer(titreCourt, pdf));

        Memoire resumeCourt = valide();
        resumeCourt.setResume("trop court");
        assertThrows(IllegalArgumentException.class, () -> service.deposer(resumeCourt, pdf));

        Memoire peuDeMots = valide();
        peuDeMots.setMotsCles("crypto, , ");
        assertThrows(IllegalArgumentException.class, () -> service.deposer(peuDeMots, pdf));

        Memoire anneeInvalide = valide();
        anneeInvalide.setAnneeAcademique("2024-2026");
        assertThrows(IllegalArgumentException.class, () -> service.deposer(anneeInvalide, pdf));

        Memoire sansDomaine = valide();
        sansDomaine.setDomaine(null);
        assertThrows(IllegalArgumentException.class, () -> service.deposer(sansDomaine, pdf));

        Memoire sansEncadreur = valide();
        sansEncadreur.setEncadreur(null);
        assertThrows(IllegalArgumentException.class, () -> service.deposer(sansEncadreur, pdf));

        Memoire encadreurEtudiant = valide();
        encadreurEtudiant.setEncadreur(etudiant);
        assertThrows(IllegalArgumentException.class, () -> service.deposer(encadreurEtudiant, pdf));

        assertTrue(memoireDAO.findAll().isEmpty());
        assertFalse(Files.exists(storage) && Files.list(storage).findAny().isPresent(),
                "aucun fichier ne doit être stocké pour un dépôt refusé");
    }

    @Test
    void fauxPdfRefuseAuDepot() throws Exception {
        SessionManager.ouvrir(etudiant);
        Path faux = dossier.resolve("faux.pdf");
        Files.writeString(faux, "pas un pdf du tout");

        assertThrows(IllegalArgumentException.class, () -> service.deposer(valide(), faux));
        assertTrue(memoireDAO.findAll().isEmpty());
    }

    @Test
    void modificationApresRefusRepasseEnAttente() throws Exception {
        SessionManager.ouvrir(etudiant);
        Memoire m = service.deposer(valide(), pdf);

        Memoire refuse = memoireDAO.findById(m.getId());
        refuse.setStatut(StatutMemoire.REFUSE);
        refuse.setMotifRefus("Résumé à revoir");
        refuse.setValidateur(encadreur);
        memoireDAO.update(refuse);

        String ancienFichier = refuse.getCheminFichier();
        Memoire corrige = valide();
        corrige.setId(m.getId());
        corrige.setTitre("Titre corrigé après le refus");
        service.modifier(corrige, BaseDeTest.creerPdf(dossier, "v2.pdf"));

        Memoire lu = memoireDAO.findById(m.getId());
        assertEquals("Titre corrigé après le refus", lu.getTitre());
        assertEquals(StatutMemoire.EN_ATTENTE, lu.getStatut());
        assertNull(lu.getMotifRefus());
        assertNull(lu.getValidateur());
        assertNotEquals(ancienFichier, lu.getCheminFichier());
        assertFalse(Files.exists(Path.of(ancienFichier)), "l'ancien PDF doit être supprimé");
        assertEquals(1, Journal.compter("MODIFICATION_MEMOIRE"));
    }

    @Test
    void memoireValideNiModifiableNiSupprimable() throws Exception {
        SessionManager.ouvrir(etudiant);
        Memoire m = service.deposer(valide(), pdf);

        Memoire v = memoireDAO.findById(m.getId());
        v.setStatut(StatutMemoire.VALIDE);
        memoireDAO.update(v);

        Memoire saisie = valide();
        saisie.setId(m.getId());
        assertThrows(IllegalStateException.class, () -> service.modifier(saisie, null));
        assertThrows(IllegalStateException.class, () -> service.supprimer(m.getId()));
    }

    @Test
    void unEtudiantNePeutPasToucherAuMemoireDUnAutre() throws Exception {
        SessionManager.ouvrir(etudiant);
        Memoire m = service.deposer(valide(), pdf);

        Utilisateur autre = BaseDeTest.creerUtilisateur("autre@test.sn", "ETU-0002",
                Role.ETUDIANT, Niveau.M2, true);
        SessionManager.ouvrir(autre);

        Memoire saisie = valide();
        saisie.setId(m.getId());
        assertThrows(SecurityException.class, () -> service.modifier(saisie, null));
        assertThrows(SecurityException.class, () -> service.supprimer(m.getId()));
        assertNotNull(memoireDAO.findById(m.getId()));
    }

    @Test
    void suppressionEffaceLaLigneEtLeFichier() throws Exception {
        SessionManager.ouvrir(etudiant);
        Memoire m = service.deposer(valide(), pdf);
        Path fichier = Path.of(m.getCheminFichier());

        service.supprimer(m.getId());

        assertNull(memoireDAO.findById(m.getId()));
        assertFalse(Files.exists(fichier));
        assertEquals(1, Journal.compter("SUPPRESSION_MEMOIRE"));
    }

    @Test
    void mesMemoiresEtListeDesEncadreursActifs() throws Exception {
        BaseDeTest.creerUtilisateur("inactif@test.sn", null, Role.ENCADREUR, null, false);
        SessionManager.ouvrir(etudiant);
        service.deposer(valide(), pdf);

        List<Memoire> miens = service.mesMemoires();
        assertEquals(1, miens.size());

        List<Utilisateur> encadreurs = service.listerEncadreurs();
        assertEquals(1, encadreurs.size());
        assertEquals(encadreur.getId(), encadreurs.get(0).getId());
    }
}

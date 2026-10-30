package com.tdsi.memotheque;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tdsi.memotheque.dao.impl.MemoireDAOImpl;
import com.tdsi.memotheque.model.Memoire;
import com.tdsi.memotheque.model.MemoireCritere;
import com.tdsi.memotheque.model.Mention;
import com.tdsi.memotheque.model.Niveau;
import com.tdsi.memotheque.model.NiveauDiffusion;
import com.tdsi.memotheque.model.Role;
import com.tdsi.memotheque.model.RoleJury;
import com.tdsi.memotheque.model.Soutenance;
import com.tdsi.memotheque.model.StatutMemoire;
import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.service.AccesService;
import com.tdsi.memotheque.service.MemoireService;
import com.tdsi.memotheque.service.ParametreService;
import com.tdsi.memotheque.service.RechercheService;
import com.tdsi.memotheque.service.SoutenanceService;
import com.tdsi.memotheque.service.ValidationService;
import com.tdsi.memotheque.util.DatabaseConnection;
import com.tdsi.memotheque.util.SessionManager;

/** Circuit complet étudiant -> encadreur -> scolarité -> jury -> publication, et règles d'accès. */
class CircuitUniversitaireTest {

    @TempDir
    Path dossier;

    private SoutenanceService soutenances;
    private MemoireDAOImpl memoireDAO;
    private Utilisateur etudiant;
    private Utilisateur encadreur;
    private Utilisateur president;
    private Utilisateur examinateur;
    private Utilisateur admin;
    private Memoire memoire;

    @BeforeEach
    void preparer() throws Exception {
        BaseDeTest.ouvrirBaseTemporaire(dossier);
        soutenances = new SoutenanceService();
        memoireDAO = new MemoireDAOImpl();
        etudiant = BaseDeTest.creerUtilisateur("etu@test.sn", "ETU-0001", Role.ETUDIANT, Niveau.M2, true);
        encadreur = BaseDeTest.creerUtilisateur("enc@test.sn", null, Role.ENCADREUR, null, true);
        president = BaseDeTest.creerUtilisateur("pres@test.sn", null, Role.ENCADREUR, null, true);
        examinateur = BaseDeTest.creerUtilisateur("exa@test.sn", null, Role.ENCADREUR, null, true);
        admin = BaseDeTest.creerUtilisateur("admin@test.sn", null, Role.ADMIN, null, true);
        memoire = BaseDeTest.deposer(dossier, etudiant, encadreur, "2025-2026", Niveau.M2);
    }

    @AfterEach
    void nettoyer() {
        BaseDeTest.fermer();
    }

    private void autoriser(Memoire m) throws Exception {
        SessionManager.ouvrir(encadreur);
        new ValidationService().donnerAvisFavorable(m.getId());
        SessionManager.ouvrir(admin);
    }

    private LocalDateTime demain(int heure) {
        return LocalDateTime.now().plusDays(1).withHour(heure).withMinute(0).withSecond(0).withNano(0);
    }

    private Soutenance planifier(Memoire m, LocalDateTime quand, String salle) throws Exception {
        return soutenances.planifier(m.getId(), quand, salle, president.getId(), List.of(examinateur.getId()));
    }

    // ------------------------------------------------------------------ planification

    @Test
    void planificationAvecJuryEtEncadreurDOffice() throws Exception {
        autoriser(memoire);
        assertEquals(1, soutenances.aPlanifier().size());

        Soutenance s = planifier(memoire, demain(10), "Amphi A");

        assertEquals(3, s.getJury().size());
        assertEquals(RoleJury.PRESIDENT, s.roleDe(president.getId()));
        assertEquals(RoleJury.EXAMINATEUR, s.roleDe(examinateur.getId()));
        assertEquals(RoleJury.ENCADREUR, s.roleDe(encadreur.getId()));
        assertTrue(soutenances.aPlanifier().isEmpty());
        assertEquals(1, Journal.compter("SOUTENANCE_PLANIFIEE"));

        // Chacun voit la soutenance qui le concerne.
        SessionManager.ouvrir(president);
        assertEquals(1, soutenances.soutenances().size());
        SessionManager.ouvrir(etudiant);
        assertEquals(1, soutenances.soutenances().size());
        assertNotNull(soutenances.soutenanceDe(memoire.getId()));
    }

    @Test
    void seulUnMemoireAutoriseSePlanifie() throws Exception {
        SessionManager.ouvrir(admin);
        assertThrows(IllegalStateException.class, () -> planifier(memoire, demain(10), "A1"),
                "pas d'avis favorable");
    }

    @Test
    void reglesDuJuryEtDeLHoraire() throws Exception {
        autoriser(memoire);
        int id = memoire.getId();
        assertThrows(IllegalArgumentException.class, () -> soutenances.planifier(id, LocalDateTime.now().minusHours(1),
                "A1", president.getId(), List.of(examinateur.getId())), "passé");
        assertThrows(IllegalArgumentException.class, () -> planifier(memoire, demain(22), "A1"), "nuit");
        assertThrows(IllegalArgumentException.class, () -> planifier(memoire, demain(10), " "), "salle");
        assertThrows(IllegalArgumentException.class, () -> soutenances.planifier(id, demain(10), "A1",
                president.getId(), List.of()), "au moins un examinateur");
        assertThrows(IllegalArgumentException.class, () -> soutenances.planifier(id, demain(10), "A1",
                encadreur.getId(), List.of(examinateur.getId())), "l'encadreur siège d'office");
        assertThrows(IllegalArgumentException.class, () -> soutenances.planifier(id, demain(10), "A1",
                president.getId(), List.of(president.getId())), "même personne deux fois");
        assertThrows(IllegalArgumentException.class, () -> soutenances.planifier(id, demain(10), "A1",
                etudiant.getId(), List.of(examinateur.getId())), "un étudiant ne siège pas");
    }

    @Test
    void conflitsDeSalleEtDeJuryDetectes() throws Exception {
        Utilisateur etu2 = BaseDeTest.creerUtilisateur("e2@test.sn", "ETU-0002", Role.ETUDIANT, Niveau.L3, true);
        Memoire autre = BaseDeTest.deposer(dossier, etu2, encadreur, "2025-2026", Niveau.L3);
        autoriser(memoire);
        autoriser(autre);
        planifier(memoire, demain(10), "Amphi A");

        Utilisateur p2 = BaseDeTest.creerUtilisateur("p2@test.sn", null, Role.ENCADREUR, null, true);
        Utilisateur x2 = BaseDeTest.creerUtilisateur("x2@test.sn", null, Role.ENCADREUR, null, true);
        int id = autre.getId();

        // Même salle à 10h30 : conflit. Même encadreur (d'office dans les deux jurys) : conflit aussi.
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> soutenances.planifier(id,
                demain(10).plusMinutes(30), "Amphi B", p2.getId(), List.of(x2.getId())));
        assertTrue(e.getMessage().contains("Conflit"));

        // À 11h (fin de la première), plus de chevauchement.
        assertNotNull(soutenances.planifier(id, demain(11), "Amphi A", p2.getId(), List.of(x2.getId())));
    }

    @Test
    void annulationPuisReplanification() throws Exception {
        autoriser(memoire);
        planifier(memoire, demain(9), "A1");
        soutenances.annuler(memoire.getId(), "Salle indisponible");
        assertEquals(1, soutenances.aPlanifier().size());
        assertEquals(StatutMemoire.AUTORISE, memoireDAO.findById(memoire.getId()).getStatut());
        assertNotNull(planifier(memoire, demain(14), "A2"));
    }

    @Test
    void seuleLaScolaritePlanifie() throws Exception {
        autoriser(memoire);
        SessionManager.ouvrir(encadreur);
        assertThrows(SecurityException.class, () -> planifier(memoire, demain(10), "A1"));
        SessionManager.ouvrir(etudiant);
        assertThrows(SecurityException.class, () -> soutenances.aPlanifier());
    }

    // ------------------------------------------------------------------ procès-verbal

    @Test
    void baremeDesMentions() {
        assertNull(SoutenanceService.mentionPour(9.99));
        assertEquals(Mention.PASSABLE, SoutenanceService.mentionPour(10));
        assertEquals(Mention.PASSABLE, SoutenanceService.mentionPour(11.99));
        assertEquals(Mention.ASSEZ_BIEN, SoutenanceService.mentionPour(12));
        assertEquals(Mention.BIEN, SoutenanceService.mentionPour(14));
        assertEquals(Mention.TRES_BIEN, SoutenanceService.mentionPour(16));
        assertEquals(Mention.TRES_BIEN, SoutenanceService.mentionPour(20));
    }

    @Test
    void pvImpossibleAvantLaSoutenanceOuAvecUneNoteInvalide() throws Exception {
        autoriser(memoire);
        planifier(memoire, demain(10), "A1");
        int id = memoire.getId();
        assertThrows(IllegalStateException.class,
                () -> soutenances.enregistrerProcesVerbal(id, 15, null, null, null, BaseDeTest.MOT_DE_PASSE));

        BaseDeTest.soutenanceDansLePasse(id);
        assertThrows(IllegalArgumentException.class,
                () -> soutenances.enregistrerProcesVerbal(id, 21, null, null, null, BaseDeTest.MOT_DE_PASSE));
        assertThrows(IllegalArgumentException.class,
                () -> soutenances.enregistrerProcesVerbal(id, -1, null, null, null, BaseDeTest.MOT_DE_PASSE));
        assertEquals(StatutMemoire.AUTORISE, memoireDAO.findById(id).getStatut());
    }

    @Test
    void admisPublieAvecMentionEtDateDeSoutenance() throws Exception {
        Memoire publie = BaseDeTest.publier(memoire, encadreur, admin, 14.25);

        assertEquals(StatutMemoire.VALIDE, publie.getStatut());
        assertEquals(Mention.BIEN, publie.getMention());
        assertEquals(LocalDate.now(), publie.getDateSoutenance());
        assertEquals(admin.getId(), publie.getValidateur().getId());
        assertEquals(1, new RechercheService().compterPublic(new MemoireCritere()));

        SessionManager.ouvrir(admin);
        assertEquals(Soutenance.Statut.TENUE, soutenances.soutenanceDe(memoire.getId()).getStatut());
    }

    @Test
    void ajournePuisCorrigePuisNouvelleSoutenance() throws Exception {
        autoriser(memoire);
        planifier(memoire, demain(10), "A1");
        BaseDeTest.soutenanceDansLePasse(memoire.getId());
        Memoire ajourne = soutenances.enregistrerProcesVerbal(memoire.getId(), 8.5, "Analyse insuffisante",
                null, null, BaseDeTest.MOT_DE_PASSE);

        assertEquals(StatutMemoire.REFUSE, ajourne.getStatut());
        assertTrue(ajourne.getMotifRefus().contains("Ajourné") && ajourne.getMotifRefus().contains("Analyse insuffisante"));
        assertNull(ajourne.getDatePublication());
        assertEquals(1, Journal.compter("PV_AJOURNEMENT"));

        // L'étudiant corrige (nouveau PDF), l'encadreur autorise de nouveau, nouvelle soutenance.
        SessionManager.ouvrir(etudiant);
        Memoire saisie = BaseDeTest.memoireValide(BaseDeTest.premierDomaine(), encadreur, "x", Niveau.M2);
        saisie.setId(memoire.getId());
        new MemoireService().modifier(saisie, BaseDeTest.creerPdf(dossier, "v2.pdf"));
        Memoire corrige = memoireDAO.findById(memoire.getId());
        assertEquals(StatutMemoire.EN_ATTENTE, corrige.getStatut());
        assertNull(corrige.getNote(), "la note de l'ajournement est effacée");

        Memoire publie = BaseDeTest.publier(corrige, encadreur, admin, 12);
        assertEquals(Mention.ASSEZ_BIEN, publie.getMention());
    }

    // ------------------------------------------------------------------ diffusion et accès

    @Test
    void diffusionRestreinteNoticeVisiblePdfReserve() throws Exception {
        Memoire m = BaseDeTest.publier(memoire, encadreur, admin, 15);
        modifierDiffusion(m.getId(), "RESTREINT", null);
        Memoire lu = memoireDAO.findById(m.getId());
        AccesService acces = new AccesService();
        Utilisateur autreEtudiant = BaseDeTest.creerUtilisateur("lecteur@test.sn", "ETU-0003", Role.ETUDIANT, Niveau.L1, true);
        Utilisateur autreEnseignant = BaseDeTest.creerUtilisateur("prof@test.sn", null, Role.ENCADREUR, null, true);

        assertTrue(acces.noticeVisible(lu, LocalDate.now()));
        assertFalse(acces.peutLirePdf(autreEtudiant, lu, LocalDate.now()));
        assertTrue(acces.peutLirePdf(autreEnseignant, lu, LocalDate.now()));
        assertTrue(acces.peutLirePdf(etudiant, lu, LocalDate.now()), "l'auteur lit toujours son mémoire");
        assertTrue(acces.peutLirePdf(admin, lu, LocalDate.now()));

        SessionManager.ouvrir(autreEtudiant);
        SecurityException e = assertThrows(SecurityException.class,
                () -> new RechercheService().preparerConsultation(lu));
        assertTrue(e.getMessage().contains("restreinte"));
    }

    @Test
    void embargoCacheDuCatalogueJusquALaDate() throws Exception {
        Memoire m = BaseDeTest.publier(memoire, encadreur, admin, 15);
        RechercheService recherche = new RechercheService();
        AccesService acces = new AccesService();
        Utilisateur lecteur = BaseDeTest.creerUtilisateur("lecteur@test.sn", "ETU-0003", Role.ETUDIANT, Niveau.L1, true);

        modifierDiffusion(m.getId(), "CONFIDENTIEL", LocalDate.now().plusMonths(6));
        Memoire lu = memoireDAO.findById(m.getId());
        assertEquals(0, recherche.compterPublic(new MemoireCritere()), "invisible pendant l'embargo");
        assertFalse(acces.peutLirePdf(lecteur, lu, LocalDate.now()));
        assertTrue(acces.peutLirePdf(lecteur, lu, LocalDate.now().plusMonths(7)), "lisible après l'embargo");
        Utilisateur presidentDuJury = new com.tdsi.memotheque.dao.impl.UtilisateurDAOImpl()
                .findByEmailOuMatricule("president.jury@test.sn");
        assertTrue(acces.peutLirePdf(presidentDuJury, lu, LocalDate.now()), "le jury garde l'accès");
        assertFalse(acces.peutLirePdf(president, lu, LocalDate.now()), "un enseignant hors jury, non");

        modifierDiffusion(m.getId(), "CONFIDENTIEL", LocalDate.now().minusDays(1));
        assertEquals(1, recherche.compterPublic(new MemoireCritere()), "embargo échu : de retour au catalogue");
    }

    @Test
    void lePvPeutFixerLaDiffusion() throws Exception {
        autoriser(memoire);
        planifier(memoire, demain(10), "A1");
        BaseDeTest.soutenanceDansLePasse(memoire.getId());
        Memoire m = soutenances.enregistrerProcesVerbal(memoire.getId(), 17, null, NiveauDiffusion.CONFIDENTIEL,
                LocalDate.now().plusYears(1), BaseDeTest.MOT_DE_PASSE);
        assertEquals(NiveauDiffusion.CONFIDENTIEL, m.getNiveauDiffusion());
        assertEquals(0, new RechercheService().compterPublic(new MemoireCritere()));
    }

    // ------------------------------------------------------------------ paramètres

    @Test
    void parametresDeLaScolarite() throws Exception {
        ParametreService p = new ParametreService();
        assertEquals("2026-2027", ParametreService.anneeParDefaut(LocalDate.of(2026, 10, 30)));
        assertEquals("2025-2026", ParametreService.anneeParDefaut(LocalDate.of(2026, 6, 1)));

        SessionManager.ouvrir(etudiant);
        assertThrows(SecurityException.class, () -> p.definir("2026-2027", true, null));

        SessionManager.ouvrir(admin);
        assertThrows(IllegalArgumentException.class, () -> p.definir("2026-2028", true, null));
        p.definir("2026-2027", false, LocalDate.of(2027, 6, 30));
        ParametreService.Campagne c = p.campagne();
        assertEquals("2026-2027", c.anneeCourante());
        assertFalse(c.ouverte());
        assertEquals(LocalDate.of(2027, 6, 30), c.dateLimite());
        assertEquals(1, Journal.compter("PARAMETRES"));
    }

    private void modifierDiffusion(int id, String diffusion, LocalDate fin) throws Exception {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE memoire SET niveau_diffusion = ?, date_fin_embargo = ? WHERE id = ?")) {
            ps.setString(1, diffusion);
            ps.setString(2, fin == null ? null : fin.toString());
            ps.setInt(3, id);
            ps.executeUpdate();
        }
    }
}

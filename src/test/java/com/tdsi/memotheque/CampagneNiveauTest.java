package com.tdsi.memotheque;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tdsi.memotheque.model.Niveau;
import com.tdsi.memotheque.model.Role;
import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.service.NotificationService;
import com.tdsi.memotheque.service.ParametreService;
import com.tdsi.memotheque.util.Dates;
import com.tdsi.memotheque.util.SessionManager;

/** Campagne de dépôt réglée par niveau : ouverture, date limite, prolongation, raccourcissement. */
class CampagneNiveauTest {

    @TempDir
    Path dossier;

    private ParametreService service;
    private Utilisateur admin;

    @BeforeEach
    void preparer() throws Exception {
        BaseDeTest.ouvrirBaseTemporaire(dossier);
        service = new ParametreService();
        admin = BaseDeTest.creerUtilisateur("admin@test.sn", null, Role.ADMIN, null, true);
        SessionManager.ouvrir(admin);
    }

    @AfterEach
    void nettoyer() {
        BaseDeTest.fermer();
    }

    @Test
    void chaqueNiveauASaPropreCampagne() throws Exception {
        LocalDate dans30 = LocalDate.now().plusDays(30);
        service.definirCampagne(Niveau.M2, true, dans30);
        service.definirCampagne(Niveau.L3, false, null);

        assertTrue(service.campagne(Niveau.M2).depotPossible(LocalDate.now()));
        assertEquals(dans30, service.campagne(Niveau.M2).dateLimite());
        assertFalse(service.campagne(Niveau.L3).depotPossible(LocalDate.now()));
        // Un niveau sans réglage propre suit le réglage général (ouvert par défaut).
        assertTrue(service.campagne(Niveau.M1).depotPossible(LocalDate.now()));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.verifierDepotPossible(Niveau.L3));
        assertTrue(e.getMessage().contains("L3"));
        assertDoesNotThrow(() -> service.verifierDepotPossible(Niveau.M2));
        assertTrue(service.resume(LocalDate.now()).contains("M2"));
        assertFalse(service.resume(LocalDate.now()).contains("L3"));
    }

    @Test
    void prolongerEtEcourterPrevientLesEtudiantsDuNiveau() throws Exception {
        Utilisateur m2 = BaseDeTest.creerUtilisateur("m2@test.sn", "M2-1", Role.ETUDIANT, Niveau.M2, true);
        Utilisateur m1 = BaseDeTest.creerUtilisateur("m1@test.sn", "M1-1", Role.ETUDIANT, Niveau.M1, true);
        LocalDate limite = LocalDate.now().plusDays(20);
        service.definirCampagne(Niveau.M2, true, limite);

        String prolongation = service.decaler(Niveau.M2, 7);
        assertEquals(limite.plusDays(7), service.campagne(Niveau.M2).dateLimite());
        assertTrue(prolongation.contains("prolongée"), prolongation);

        String raccourci = service.decaler(Niveau.M2, -10);
        assertEquals(limite.minusDays(3), service.campagne(Niveau.M2).dateLimite());
        assertTrue(raccourci.contains("avancée"), raccourci);

        // Même réglage : aucun changement, aucune notification.
        assertNull(service.definirCampagne(Niveau.M2, true, limite.minusDays(3)));

        SessionManager.ouvrir(m2);
        assertEquals(3, new NotificationService().mesNotifications().size());
        SessionManager.ouvrir(m1);
        assertTrue(new NotificationService().mesNotifications().isEmpty());
    }

    @Test
    void dateLimitePasseeRefuseePourUneCampagneOuverte() {
        assertThrows(IllegalArgumentException.class,
                () -> service.definirCampagne(Niveau.M1, true, LocalDate.now().minusDays(1)));
        assertDoesNotThrow(() -> service.definirCampagne(Niveau.M1, false, LocalDate.now().minusDays(1)));
    }

    @Test
    void reglageReserveALAdministration() throws Exception {
        Utilisateur etu = BaseDeTest.creerUtilisateur("e@test.sn", "E-1", Role.ETUDIANT, Niveau.M2, true);
        SessionManager.ouvrir(etu);
        assertThrows(SecurityException.class, () -> service.definirCampagne(Niveau.M2, false, null));
        assertThrows(SecurityException.class, () -> service.decaler(Niveau.M2, 7));
    }

    @Test
    void reglageGeneralEffaceLesReglagesParNiveau() throws Exception {
        service.definirCampagne(Niveau.M2, false, null);
        service.definir("2026-2027", true, LocalDate.now().plusDays(10));
        assertTrue(service.campagne(Niveau.M2).ouverte());
        assertEquals(LocalDate.now().plusDays(10), service.campagne(Niveau.M2).dateLimite());
    }

    @Test
    void saisieDesDatesEtHeures() {
        assertEquals(LocalDate.of(2027, 6, 30), Dates.analyser("30/06/2027"));
        assertEquals(LocalDate.of(2027, 6, 30), Dates.analyser(" 30-06-2027 "));
        assertEquals(LocalDate.of(2027, 6, 3), Dates.analyser("3.6.2027"));
        assertEquals(LocalDate.of(2027, 6, 30), Dates.analyser("2027-06-30"));
        assertNull(Dates.analyser("  "));
        assertThrows(IllegalArgumentException.class, () -> Dates.analyser("31/02/2027"));
        assertThrows(IllegalArgumentException.class, () -> Dates.analyser("demain"));

        assertEquals(LocalTime.of(9, 30), Dates.heure("9h30"));
        assertEquals(LocalTime.of(9, 30), Dates.heure("09:30"));
        assertEquals(LocalTime.of(14, 0), Dates.heure("14h"));
        assertEquals(LocalTime.of(14, 0), Dates.heure("14"));
        assertThrows(IllegalArgumentException.class, () -> Dates.heure("25:00"));
        assertThrows(IllegalArgumentException.class, () -> Dates.heure(""));
        assertEquals("30/06/2027", Dates.format(LocalDate.of(2027, 6, 30)));
    }
}

package com.tdsi.memotheque;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tdsi.memotheque.model.Memoire;
import com.tdsi.memotheque.model.Niveau;
import com.tdsi.memotheque.model.Notification;
import com.tdsi.memotheque.model.Role;
import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.service.CompteService;
import com.tdsi.memotheque.service.NotificationService;
import com.tdsi.memotheque.service.ValidationService;
import com.tdsi.memotheque.util.SessionManager;

/** Notifications internes déclenchées par les actions métier. */
class NotificationTest {

    @TempDir
    Path dossier;

    private NotificationService notifications;
    private Utilisateur etudiant;
    private Utilisateur encadreur;
    private Utilisateur admin;

    @BeforeEach
    void preparer() throws Exception {
        BaseDeTest.ouvrirBaseTemporaire(dossier);
        notifications = new NotificationService();
        etudiant = BaseDeTest.creerUtilisateur("etu@test.sn", "ETU-0001", Role.ETUDIANT, Niveau.M2, true);
        encadreur = BaseDeTest.creerUtilisateur("enc@test.sn", null, Role.ENCADREUR, null, true);
        admin = BaseDeTest.creerUtilisateur("admin@test.sn", null, Role.ADMIN, null, true);
    }

    @AfterEach
    void nettoyer() {
        BaseDeTest.fermer();
    }

    private List<Notification> de(Utilisateur u) throws Exception {
        SessionManager.ouvrir(u);
        List<Notification> l = notifications.mesNotifications();
        SessionManager.fermer();
        return l;
    }

    @Test
    void depotPuisRefusPuisValidation() throws Exception {
        Memoire m = BaseDeTest.deposer(dossier, etudiant, encadreur, "2024-2025", Niveau.M2);

        List<Notification> pourEncadreur = de(encadreur);
        assertEquals(1, pourEncadreur.size());
        assertTrue(pourEncadreur.get(0).getMessage().startsWith("Nouveau mémoire à valider"));
        assertTrue(de(etudiant).isEmpty(), "chacun ne voit que ses notifications");

        SessionManager.ouvrir(encadreur);
        new ValidationService().refuser(m.getId(), "Plan à restructurer");
        SessionManager.fermer();
        assertTrue(de(etudiant).get(0).getMessage().contains("Plan à restructurer"));

        Utilisateur etu2 = BaseDeTest.creerUtilisateur("etu2@test.sn", "ETU-0002", Role.ETUDIANT, Niveau.L3, true);
        Memoire m2 = BaseDeTest.deposer(dossier, etu2, encadreur, "2024-2025", Niveau.L3);
        SessionManager.ouvrir(encadreur);
        new ValidationService().valider(m2.getId(), BaseDeTest.MOT_DE_PASSE);
        SessionManager.fermer();
        assertTrue(de(etu2).get(0).getMessage().startsWith("Félicitations"));
    }

    @Test
    void activationDeCompteEtAlerteAdmins() throws Exception {
        Utilisateur inactif = BaseDeTest.creerUtilisateur("new@test.sn", "ETU-0009", Role.ETUDIANT, Niveau.L1, false);
        notifications.notifierAdmins("Nouvelle inscription à activer : new@test.sn");
        assertEquals(1, de(admin).size());

        SessionManager.ouvrir(admin);
        new CompteService().activer(inactif.getId());
        SessionManager.fermer();
        assertTrue(de(inactif).get(0).getMessage().contains("activé"));
    }

    @Test
    void compteurLectureEtSuppression() throws Exception {
        notifications.notifier(etudiant.getId(), "un");
        notifications.notifier(etudiant.getId(), "deux");
        SessionManager.ouvrir(etudiant);

        assertEquals(2, notifications.compterNonLues());
        notifications.marquerToutesLues();
        assertEquals(0, notifications.compterNonLues());
        assertTrue(notifications.mesNotifications().stream().allMatch(Notification::isLue));

        notifications.notifier(etudiant.getId(), "trois");
        notifications.supprimerLues();
        List<Notification> restantes = notifications.mesNotifications();
        assertEquals(1, restantes.size());
        assertEquals("trois", restantes.get(0).getMessage());
    }

    @Test
    void lectureExigeUneConnexionEtMessageBorne() throws Exception {
        assertThrows(SecurityException.class, () -> notifications.mesNotifications());
        assertEquals(0, notifications.compterNonLues());

        notifications.notifier(etudiant.getId(), "x".repeat(2000));
        assertEquals(501, de(etudiant).get(0).getMessage().length());
    }
}

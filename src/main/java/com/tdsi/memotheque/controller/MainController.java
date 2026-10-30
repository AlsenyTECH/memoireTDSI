package com.tdsi.memotheque.controller;

import java.sql.SQLException;

import com.tdsi.memotheque.model.Role;
import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.service.JournalService;
import com.tdsi.memotheque.service.NotificationService;
import com.tdsi.memotheque.util.SceneNavigator;
import com.tdsi.memotheque.util.SessionManager;
import com.tdsi.memotheque.util.SurveillanceInactivite;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * [M1] Cadre principal : menu à gauche, contenu au centre.
 * Le menu affiché dépend du rôle de l'utilisateur connecté (SessionManager).
 * ATTENTION : masquer un bouton n'est qu'un confort d'affichage.
 * La vraie vérification des droits est faite dans les services.
 */
public class MainController {

    /** Cadre affiché (pour rafraîchir le compteur de notifications depuis un autre écran). */
    private static MainController instance;

    @FXML private StackPane contenu;

    @FXML private Label lblUtilisateur;
    @FXML private Label lblRole;

    @FXML private Button btnDashboard;
    @FXML private Button btnRecherche;
    @FXML private Button btnDepot;
    @FXML private Button btnMesMemoires;
    @FXML private Button btnValidation;
    @FXML private Button btnSuivi;
    @FXML private Button btnSoutenances;
    @FXML private Button btnParametres;
    @FXML private Button btnComptes;
    @FXML private Button btnDomaines;
    @FXML private Button btnJournal;
    @FXML private Button btnSauvegarde;
    @FXML private Button btnNotifications;
    @FXML private Button btnProfil;

    private final NotificationService notificationService = new NotificationService();
    private Timeline rafraichissement;

    @FXML
    private void initialize() {
        Utilisateur u = SessionManager.getUtilisateur();

        // Personne n'est connecté : retour à l'écran de connexion (plus de « mode développement »).
        if (u == null) {
            javafx.application.Platform.runLater(SceneNavigator::afficherLogin);
            return;
        }

        instance = this;
        configurerMenu(u);
        SceneNavigator.definirZoneContenu(contenu);

        contenu.sceneProperty().addListener((obs, ancienne, scene) -> {
            if (scene != null) {
                // Déconnexion automatique après inactivité.
                SurveillanceInactivite.demarrer(scene);
                installerRaccourcis(scene);
            }
        });

        // Compteur de notifications : à l'ouverture puis toutes les 60 secondes.
        rafraichirNotifications();
        rafraichissement = new Timeline(new KeyFrame(Duration.seconds(60), e -> rafraichirNotifications()));
        rafraichissement.setCycleCount(Animation.INDEFINITE);
        rafraichissement.play();

        // Mot de passe temporaire : seul le profil est accessible tant qu'il n'est pas changé.
        SceneNavigator.charger(u.isDoitChangerMotDePasse() ? "profil" : "dashboard");
    }

    /** Appelé par l'écran Notifications après lecture. */
    public static void rafraichirCompteur() {
        if (instance != null) {
            instance.rafraichirNotifications();
        }
    }

    private void rafraichirNotifications() {
        if (!SessionManager.estConnecte()) {
            return;
        }
        try {
            int n = notificationService.compterNonLues();
            btnNotifications.setText(n == 0 ? "Notifications" : "Notifications (" + n + ")");
            btnNotifications.getStyleClass().remove("menu-alerte");
            if (n > 0) {
                btnNotifications.getStyleClass().add("menu-alerte");
            }
        } catch (SQLException e) {
            btnNotifications.setText("Notifications");
        }
    }

    /** Affiche ou masque les boutons selon le rôle (voir le tableau « menus par rôle »). */
    private void configurerMenu(Utilisateur u) {
        lblUtilisateur.setText(u.getNomComplet());
        lblRole.setText(u.getRole() == null ? "" : u.getRole().getLibelle());

        // Pour tous les rôles
        afficher(btnDashboard, true);
        afficher(btnRecherche, true);
        afficher(btnNotifications, true);
        afficher(btnProfil, true);

        // Étudiant seulement
        boolean etudiant = SessionManager.aLeRole(Role.ETUDIANT);
        afficher(btnDepot, etudiant);
        afficher(btnMesMemoires, etudiant);

        // Encadreur et admin (scolarité)
        boolean enseignantOuAdmin = SessionManager.aLeRole(Role.ENCADREUR, Role.ADMIN);
        boolean admin = SessionManager.aLeRole(Role.ADMIN);
        afficher(btnValidation, enseignantOuAdmin);
        afficher(btnSuivi, enseignantOuAdmin);
        afficher(btnSoutenances, enseignantOuAdmin);
        btnSuivi.setText(admin ? "Suivi des mémoires" : "Mes encadrements");
        btnSoutenances.setText(admin ? "Soutenances" : "Mes soutenances (jury)");

        // Admin seulement
        afficher(btnParametres, admin);
        afficher(btnComptes, admin);
        afficher(btnDomaines, admin);
        afficher(btnJournal, admin);
        afficher(btnSauvegarde, admin);

        if (u.isDoitChangerMotDePasse()) {
            for (Button b : new Button[] {btnDashboard, btnRecherche, btnDepot, btnMesMemoires,
                    btnValidation, btnSuivi, btnSoutenances, btnParametres, btnComptes, btnDomaines, btnJournal,
                    btnSauvegarde, btnNotifications}) {
                b.setDisable(true);
            }
        }
    }

    /** Ctrl+D tableau de bord, Ctrl+F recherche, Ctrl+N notifications, Ctrl+P profil, Ctrl+Q déconnexion. */
    private void installerRaccourcis(Scene scene) {
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.D, KeyCombination.SHORTCUT_DOWN), this::allerDashboard);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN), this::allerRecherche);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN), this::allerNotifications);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.P, KeyCombination.SHORTCUT_DOWN), this::allerProfil);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.Q, KeyCombination.SHORTCUT_DOWN), this::deconnexion);
    }

    /** Un bouton masqué ne doit pas non plus laisser de trou dans le menu (setManaged). */
    private void afficher(Button bouton, boolean visible) {
        bouton.setVisible(visible);
        bouton.setManaged(visible);
    }

    @FXML private void allerDashboard()     { SceneNavigator.charger("dashboard"); }
    @FXML private void allerRecherche()     { SceneNavigator.charger("recherche"); }
    @FXML private void allerDepot()         { SceneNavigator.charger("depot"); }
    @FXML private void allerMesMemoires()   { SceneNavigator.charger("mes_memoires"); }
    @FXML private void allerValidation()    { SceneNavigator.charger("validation"); }
    @FXML private void allerSuivi()         { SceneNavigator.charger("suivi"); }
    @FXML private void allerSoutenances()   { SceneNavigator.charger("soutenances"); }
    @FXML private void allerParametres()    { SceneNavigator.charger("parametres"); }
    @FXML private void allerComptes()       { SceneNavigator.charger("comptes"); }
    @FXML private void allerDomaines()      { SceneNavigator.charger("domaines"); }
    @FXML private void allerJournal()       { SceneNavigator.charger("journal"); }
    @FXML private void allerSauvegarde()    { SceneNavigator.charger("sauvegarde"); }
    @FXML private void allerNotifications() { SceneNavigator.charger("notifications"); }
    @FXML private void allerProfil()        { SceneNavigator.charger("profil"); }

    @FXML
    private void deconnexion() {
        SurveillanceInactivite.arreter();
        if (rafraichissement != null) {
            rafraichissement.stop();
        }
        instance = null;
        Utilisateur u = SessionManager.getUtilisateur();
        if (u != null) {
            new JournalService().log(u.getId(), "DECONNEXION", u.getEmail());
        }
        SessionManager.fermer();
        SceneNavigator.afficherLogin();
    }
}

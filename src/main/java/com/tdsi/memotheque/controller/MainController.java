package com.tdsi.memotheque.controller;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

import com.tdsi.memotheque.model.Role;
import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.service.JournalService;
import com.tdsi.memotheque.service.NotificationService;
import com.tdsi.memotheque.util.Icone;
import com.tdsi.memotheque.util.SceneNavigator;
import com.tdsi.memotheque.util.SessionManager;
import com.tdsi.memotheque.util.SurveillanceInactivite;
import com.tdsi.memotheque.util.Theme;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * [M1] Cadre principal : barre latérale à icônes, barre du haut (titre de l'écran, notifications,
 * thème, profil, déconnexion), contenu au centre.
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
    @FXML private Label lblInitiales;
    @FXML private Label lblFil;
    @FXML private Label lblTitre;
    @FXML private Label badgeNotifications;
    @FXML private Label lblSectionEtudiant;
    @FXML private Label lblSectionCircuit;
    @FXML private Label lblSectionAdmin;

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
    @FXML private Button btnTheme;
    @FXML private Button btnDeconnexion;

    private final NotificationService notificationService = new NotificationService();
    private final Map<String, Button> boutonsParVue = new LinkedHashMap<>();
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
        installerIcones();
        configurerMenu(u);
        SceneNavigator.definirZoneContenu(contenu);
        SceneNavigator.surNavigation(this::marquerEcran);

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

    private void installerIcones() {
        Object[][] menu = {
            {btnDashboard, Icone.TABLEAU_DE_BORD, "dashboard"},
            {btnRecherche, Icone.RECHERCHE, "recherche"},
            {btnDepot, Icone.DEPOT, "depot"},
            {btnMesMemoires, Icone.MES_MEMOIRES, "mes_memoires"},
            {btnValidation, Icone.EVALUER, "validation"},
            {btnSuivi, Icone.SUIVI, "suivi"},
            {btnSoutenances, Icone.SOUTENANCES, "soutenances"},
            {btnParametres, Icone.CAMPAGNE, "parametres"},
            {btnComptes, Icone.COMPTES, "comptes"},
            {btnDomaines, Icone.DOMAINES, "domaines"},
            {btnJournal, Icone.JOURNAL, "journal"},
            {btnSauvegarde, Icone.SAUVEGARDE, "sauvegarde"},
        };
        for (Object[] m : menu) {
            Button b = (Button) m[0];
            b.setGraphic(((Icone) m[1]).creer(18));
            boutonsParVue.put((String) m[2], b);
        }
        btnNotifications.setGraphic(Icone.NOTIFICATIONS.creer(21));
        btnProfil.setGraphic(Icone.PROFIL.creer(21));
        btnDeconnexion.setGraphic(Icone.DECONNEXION.creer(20));
        majIconeTheme();
    }

    private void majIconeTheme() {
        btnTheme.setGraphic((Theme.estSombre() ? Icone.CLAIR : Icone.SOMBRE).creer(20));
    }

    /** Surligne l'entrée du menu et affiche le titre de l'écran courant. */
    private void marquerEcran(String vue) {
        boutonsParVue.values().forEach(b -> b.getStyleClass().remove("actif"));
        Button b = boutonsParVue.get(vue);
        if (b != null) {
            b.getStyleClass().add("actif");
        }
        String titre = switch (vue) {
            case "notifications" -> "Notifications";
            case "profil" -> "Mon profil";
            case "detail_memoire" -> "Fiche du mémoire";
            default -> b != null ? b.getText() : "MémoTDSI";
        };
        lblTitre.setText(titre);
        lblFil.setText(section(vue));
        // Le titre est dans la barre du haut : on retire celui de l'écran pour ne pas le répéter.
        contenu.applyCss();
        contenu.lookupAll(".titre-page").forEach(n -> {
            n.setVisible(false);
            n.setManaged(false);
        });
    }

    private String section(String vue) {
        return switch (vue) {
            case "depot", "mes_memoires" -> "MémoTDSI · Mon mémoire";
            case "validation", "suivi", "soutenances", "parametres" ->
                    "MémoTDSI · " + (SessionManager.aLeRole(Role.ADMIN) ? "Scolarité" : "Enseignement");
            case "comptes", "domaines", "journal", "sauvegarde" -> "MémoTDSI · Administration";
            case "detail_memoire" -> "MémoTDSI · Catalogue";
            default -> "MémoTDSI";
        };
    }

    private void rafraichirNotifications() {
        if (!SessionManager.estConnecte()) {
            return;
        }
        try {
            int n = notificationService.compterNonLues();
            badgeNotifications.setText(n > 99 ? "99+" : String.valueOf(n));
            badgeNotifications.setVisible(n > 0);
        } catch (SQLException e) {
            badgeNotifications.setVisible(false);
        }
    }

    /** Affiche ou masque les entrées selon le rôle (voir le tableau « menus par rôle »). */
    private void configurerMenu(Utilisateur u) {
        lblUtilisateur.setText(u.getNomComplet());
        lblRole.setText(u.getRole() == null ? "" : u.getRole() == Role.ADMIN ? "Scolarité" : u.getRole().getLibelle());
        lblInitiales.setText(initiales(u));

        boolean etudiant = SessionManager.aLeRole(Role.ETUDIANT);
        boolean admin = SessionManager.aLeRole(Role.ADMIN);
        boolean enseignantOuAdmin = SessionManager.aLeRole(Role.ENCADREUR, Role.ADMIN);

        afficher(lblSectionEtudiant, etudiant);
        afficher(btnDepot, etudiant);
        afficher(btnMesMemoires, etudiant);

        afficher(lblSectionCircuit, enseignantOuAdmin);
        lblSectionCircuit.setText(admin ? "SCOLARITÉ" : "ENSEIGNEMENT");
        afficher(btnValidation, enseignantOuAdmin);
        afficher(btnSuivi, enseignantOuAdmin);
        afficher(btnSoutenances, enseignantOuAdmin);
        btnSuivi.setText(admin ? "Suivi des mémoires" : "Mes encadrements");
        btnSoutenances.setText(admin ? "Soutenances" : "Mes jurys");
        afficher(btnParametres, admin);

        afficher(lblSectionAdmin, admin);
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

    private static String initiales(Utilisateur u) {
        String p = u.getPrenom() == null || u.getPrenom().isBlank() ? "" : u.getPrenom().trim().substring(0, 1);
        String n = u.getNom() == null || u.getNom().isBlank() ? "" : u.getNom().trim().substring(0, 1);
        String i = (p + n).toUpperCase();
        return i.isEmpty() ? "?" : i;
    }

    /** Ctrl+D tableau de bord, Ctrl+F recherche, Ctrl+N notifications, Ctrl+P profil, Ctrl+Q déconnexion. */
    private void installerRaccourcis(Scene scene) {
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.D, KeyCombination.SHORTCUT_DOWN), this::allerDashboard);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN), this::allerRecherche);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN), this::allerNotifications);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.P, KeyCombination.SHORTCUT_DOWN), this::allerProfil);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.Q, KeyCombination.SHORTCUT_DOWN), this::deconnexion);
    }

    /** Un élément masqué ne doit pas non plus laisser de trou dans le menu (setManaged). */
    private void afficher(Node n, boolean visible) {
        n.setVisible(visible);
        n.setManaged(visible);
    }

    @FXML
    private void basculerTheme() {
        Theme.choisirSombre(!Theme.estSombre(), btnTheme.getScene());
        majIconeTheme();
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

package com.tdsi.memotheque.controller;

import com.tdsi.memotheque.model.Role;
import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.util.SceneNavigator;
import com.tdsi.memotheque.util.SessionManager;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

/**
 * [M1] Cadre principal : menu à gauche, contenu au centre.
 * Le menu affiché dépend du rôle de l'utilisateur connecté (SessionManager).
 * ATTENTION : masquer un bouton n'est qu'un confort d'affichage.
 * La vraie vérification des droits est faite dans les services.
 */
public class MainController {

    @FXML private StackPane contenu;

    @FXML private Label lblUtilisateur;
    @FXML private Label lblRole;

    @FXML private Button btnDashboard;
    @FXML private Button btnRecherche;
    @FXML private Button btnDepot;
    @FXML private Button btnMesMemoires;
    @FXML private Button btnValidation;
    @FXML private Button btnComptes;
    @FXML private Button btnDomaines;
    @FXML private Button btnJournal;
    @FXML private Button btnProfil;

    @FXML
    private void initialize() {
        Utilisateur u = SessionManager.getUtilisateur();

        // Personne n'est connecté : retour à l'écran de connexion (plus de « mode développement »).
        if (u == null) {
            javafx.application.Platform.runLater(SceneNavigator::afficherLogin);
            return;
        }

        configurerMenu(u);
        SceneNavigator.definirZoneContenu(contenu);
        SceneNavigator.charger("dashboard");
    }

    /** Affiche ou masque les boutons selon le rôle (voir le tableau « menus par rôle »). */
    private void configurerMenu(Utilisateur u) {
        lblUtilisateur.setText(u.getNomComplet());
        lblRole.setText(u.getRole() == null ? "" : u.getRole().getLibelle());

        // Pour tous les rôles
        afficher(btnDashboard, true);
        afficher(btnRecherche, true);
        afficher(btnProfil, true);

        // Étudiant seulement
        boolean etudiant = SessionManager.aLeRole(Role.ETUDIANT);
        afficher(btnDepot, etudiant);
        afficher(btnMesMemoires, etudiant);

        // Encadreur et admin
        afficher(btnValidation, SessionManager.aLeRole(Role.ENCADREUR, Role.ADMIN));

        // Admin seulement
        boolean admin = SessionManager.aLeRole(Role.ADMIN);
        afficher(btnComptes, admin);
        afficher(btnDomaines, admin);
        afficher(btnJournal, admin);
    }

    /** Un bouton masqué ne doit pas non plus laisser de trou dans le menu (setManaged). */
    private void afficher(Button bouton, boolean visible) {
        bouton.setVisible(visible);
        bouton.setManaged(visible);
    }

    @FXML private void allerDashboard()   { SceneNavigator.charger("dashboard"); }
    @FXML private void allerRecherche()   { SceneNavigator.charger("recherche"); }
    @FXML private void allerDepot()       { SceneNavigator.charger("depot"); }
    @FXML private void allerMesMemoires() { SceneNavigator.charger("mes_memoires"); }
    @FXML private void allerValidation()  { SceneNavigator.charger("validation"); }
    @FXML private void allerComptes()     { SceneNavigator.charger("comptes"); }
    @FXML private void allerDomaines()    { SceneNavigator.charger("domaines"); }
    @FXML private void allerJournal()     { SceneNavigator.charger("journal"); }
    @FXML private void allerProfil()      { SceneNavigator.charger("profil"); }

    @FXML
    private void deconnexion() {
        SessionManager.fermer();
        SceneNavigator.afficherLogin();
    }
}
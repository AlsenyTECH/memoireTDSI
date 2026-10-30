package com.tdsi.memotheque;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.tdsi.memotheque.service.AuthService;
import com.tdsi.memotheque.service.MemoireService;
import com.tdsi.memotheque.util.AlertUtil;
import com.tdsi.memotheque.util.DatabaseConnection;
import com.tdsi.memotheque.util.SceneNavigator;
import com.tdsi.memotheque.util.Validator;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

/** [M1] Point d'entrée JavaFX : initialise la base puis affiche l'écran de connexion. */
public class MainApp extends Application {

    private static final Logger LOG = Logger.getLogger(MainApp.class.getName());

    @Override
    public void start(Stage stage) {
        try {
            DatabaseConnection.initialiser();
            AuthService auth = new AuthService();

            try {
                // [M2] Comptes de démonstration (mots de passe lus dans les variables MEMOTDSI_*_PASSWORD).
                auth.initialiserAdminParDefaut();
            } catch (IllegalStateException e) {
                LOG.info(() -> "Comptes de démonstration non créés : " + e.getMessage());
            }

            // Mode démonstration (mvn javafx:run) : comptes admin@mcs, samb@mcs, babs@mcs et données de test.
            if (com.tdsi.memotheque.service.DonneesDemo.active()) {
                try {
                    new com.tdsi.memotheque.service.DonneesDemo().charger();
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Jeu de démonstration non créé", e);
                }
            }

            // Installation neuve sans administrateur : on le crée maintenant.
            if (!auth.adminExiste() && !creerPremierAdmin(auth)) {
                Platform.exit();
                return;
            }

            try {
                // PDF déposés avant l'activation du chiffrement : chiffrés une fois pour toutes.
                new MemoireService().chiffrerAnciensFichiers();
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Chiffrement des anciens PDF interrompu", e);
            }

            SceneNavigator.init(stage);
            SceneNavigator.afficherLogin();
            stage.setMinWidth(900);
            stage.setMinHeight(600);
            stage.show();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Démarrage impossible", e);
            AlertUtil.erreur("Démarrage impossible", e.getMessage());
        }
    }

    /** Premier démarrage : formulaire de création du compte administrateur. */
    private boolean creerPremierAdmin(AuthService auth) {
        TextField prenom = new TextField();
        TextField nom = new TextField();
        TextField email = new TextField();
        PasswordField mdp = new PasswordField();
        PasswordField confirmation = new PasswordField();
        Label erreur = new Label();
        erreur.setStyle("-fx-text-fill: #b42318;");
        erreur.setWrapText(true);

        GridPane grille = new GridPane();
        grille.setHgap(10);
        grille.setVgap(8);
        grille.setPadding(new Insets(10));
        grille.addRow(0, new Label("Prénom"), prenom);
        grille.addRow(1, new Label("Nom"), nom);
        grille.addRow(2, new Label("E-mail"), email);
        grille.addRow(3, new Label("Mot de passe"), mdp);
        grille.addRow(4, new Label("Confirmation"), confirmation);
        grille.add(erreur, 0, 5, 2, 1);

        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("MémoTDSI - Premier démarrage");
        d.setHeaderText("Bienvenue ! Créez le compte administrateur de la plateforme.\n"
                + "Mot de passe : au moins 12 caractères, avec lettres et chiffres.");
        d.getDialogPane().setContent(grille);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        while (true) {
            if (d.showAndWait().filter(b -> b == ButtonType.OK).isEmpty()) {
                return false;
            }
            if (!mdp.getText().equals(confirmation.getText())) {
                erreur.setText("Les deux mots de passe ne correspondent pas.");
                continue;
            }
            if (!Validator.emailValide(email.getText())) {
                erreur.setText("Adresse e-mail invalide.");
                continue;
            }
            try {
                auth.creerPremierAdmin(nom.getText(), prenom.getText(), email.getText(), mdp.getText());
                AlertUtil.info("Premier démarrage", "Compte administrateur créé. Connectez-vous avec "
                        + email.getText().trim().toLowerCase() + ".");
                return true;
            } catch (IllegalArgumentException | SecurityException e) {
                erreur.setText(e.getMessage());
            } catch (Exception e) {
                erreur.setText("Erreur : " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}

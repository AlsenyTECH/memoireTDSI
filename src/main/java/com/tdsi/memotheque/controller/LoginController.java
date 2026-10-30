package com.tdsi.memotheque.controller;

import java.sql.SQLException;

import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.service.AuthService;
import com.tdsi.memotheque.service.JournalService;
import com.tdsi.memotheque.util.SceneNavigator;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

/**
 * [M2] Écran de connexion.
 * À faire : lire les champs login / mot de passe, appeler AuthService.connecter(...),
 * afficher le message d'erreur, puis SceneNavigator.afficherCadrePrincipal() si succès.
 *
 * La journalisation CONNEXION / ECHEC_CONNEXION est faite ici (AuthService n'est pas modifié).
 * Le mot de passe n'est jamais écrit dans le journal.
 */
public class LoginController {

    @FXML private TextField champIdentifiant;
    @FXML private PasswordField champMotDePasse;
    @FXML private Label labelMessage;
    @FXML private javafx.scene.layout.StackPane panneauImage;
    @FXML private Label labelCredit;
    @FXML private javafx.scene.layout.VBox encartDemo;

    /** Une vraie photo du campus déposée par l'établissement remplace l'illustration. */
    @FXML
    private void initialize() {
        String photo = com.tdsi.memotheque.util.Images.photoCampus();
        if (photo != null && panneauImage != null) {
            panneauImage.setStyle("-fx-background-image: url(\"" + photo + "\");");
            labelCredit.setText("Photo : campus de l'université");
        }
        // Mode démonstration : un clic remplit l'identifiant et le mot de passe d'un compte de test.
        if (com.tdsi.memotheque.service.DonneesDemo.active()) {
            for (com.tdsi.memotheque.service.DonneesDemo.Compte c : com.tdsi.memotheque.service.DonneesDemo.COMPTES) {
                javafx.scene.control.Hyperlink lien = new javafx.scene.control.Hyperlink(
                        c.nom() + " (" + c.role().toLowerCase() + ")\n" + c.identifiant() + "  ·  mot de passe " + c.motDePasse());
                lien.setWrapText(true);
                lien.setOnAction(e -> {
                    champIdentifiant.setText(c.identifiant());
                    champMotDePasse.setText(c.motDePasse());
                    champMotDePasse.requestFocus();
                });
                encartDemo.getChildren().add(lien);
            }
            encartDemo.setVisible(true);
            encartDemo.setManaged(true);
        }
    }

    private final AuthService authService = new AuthService();
    private final JournalService journalService = new JournalService();

    @FXML
    private void seConnecter() {
        String identifiant = champIdentifiant.getText();

        try {
            Utilisateur u = authService.connecter(identifiant, champMotDePasse.getText());
            champMotDePasse.clear();
            journalService.log(u.getId(), "CONNEXION", u.getEmail());
            SceneNavigator.afficherCadrePrincipal();

        } catch (IllegalArgumentException e) {
            champMotDePasse.clear();
            labelMessage.setText(e.getMessage());
            journalService.log(null, "ECHEC_CONNEXION",
                    "Identifiant saisi : " + tronquer(identifiant) + " (" + e.getMessage() + ")");
        } catch (SQLException e) {
            labelMessage.setText("Erreur d'accès à la base : " + e.getMessage());
        }
    }

    @FXML
    private void allerInscription() {
        SceneNavigator.afficherInscription();
    }

    private String tronquer(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        return t.length() > 100 ? t.substring(0, 100) + "..." : t;
    }
}

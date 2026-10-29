package com.tdsi.memotheque.controller;

import java.sql.SQLException;

import com.tdsi.memotheque.model.Niveau;
import com.tdsi.memotheque.service.AuthService;
import com.tdsi.memotheque.service.JournalService;
import com.tdsi.memotheque.util.AlertUtil;
import com.tdsi.memotheque.util.SceneNavigator;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

/** [M2] Inscription d'un « tdsien » (matricule, niveau, email). Le compte est créé inactif (activation par l'admin). */
public class InscriptionController {

    @FXML private TextField champMatricule;
    @FXML private TextField champNom;
    @FXML private TextField champPrenom;
    @FXML private TextField champEmail;
    @FXML private ComboBox<Niveau> comboNiveau;
    @FXML private PasswordField champMotDePasse;
    @FXML private PasswordField champConfirmation;
    @FXML private Label labelMessage;

    private final AuthService authService = new AuthService();
    private final JournalService journalService = new JournalService();

    @FXML
    private void initialize() {
        comboNiveau.setItems(FXCollections.observableArrayList(Niveau.values()));
    }

    @FXML
    private void inscrire() {
        String motDePasse = champMotDePasse.getText();

        if (motDePasse == null || !motDePasse.equals(champConfirmation.getText())) {
            labelMessage.setText("Les deux mots de passe ne correspondent pas.");
            return;
        }

        try {
            authService.inscrire(champMatricule.getText(), champNom.getText(), champPrenom.getText(),
                    champEmail.getText(), motDePasse, comboNiveau.getValue());

            journalService.log(null, "INSCRIPTION",
                    "Compte étudiant créé (inactif) : " + champEmail.getText().trim().toLowerCase());

            AlertUtil.info("Inscription",
                    "Votre compte a été créé. Il sera utilisable après activation par l'administrateur.");
            SceneNavigator.afficherLogin();

        } catch (IllegalArgumentException e) {
            labelMessage.setText(e.getMessage());
        } catch (SQLException e) {
            labelMessage.setText("Erreur d'accès à la base : " + e.getMessage());
        } finally {
            champMotDePasse.clear();
            champConfirmation.clear();
        }
    }

    @FXML
    private void retourConnexion() {
        SceneNavigator.afficherLogin();
    }
}

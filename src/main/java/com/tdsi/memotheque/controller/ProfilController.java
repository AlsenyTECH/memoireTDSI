package com.tdsi.memotheque.controller;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;

import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.service.CompteService;
import com.tdsi.memotheque.util.SessionManager;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;

/** [M2] Profil de l'utilisateur connecté et changement de mot de passe. */
public class ProfilController {

    @FXML private Label labelNom;
    @FXML private Label labelEmail;
    @FXML private Label labelMatricule;
    @FXML private Label labelRole;
    @FXML private Label labelNiveau;
    @FXML private Label labelDateCreation;
    @FXML private PasswordField champAncien;
    @FXML private PasswordField champNouveau;
    @FXML private PasswordField champConfirmation;
    @FXML private Label labelMessage;

    private final CompteService compteService = new CompteService();

    @FXML
    private void initialize() {
        Utilisateur u = SessionManager.getUtilisateur();
        if (u == null) {
            labelMessage.setText("Aucun utilisateur connecté.");
            return;
        }
        labelNom.setText(u.getNomComplet());
        labelEmail.setText(u.getEmail());
        labelMatricule.setText(u.getMatricule() == null ? "-" : u.getMatricule());
        labelRole.setText(u.getRole() == null ? "-" : u.getRole().getLibelle());
        labelNiveau.setText(u.getNiveau() == null ? "-" : u.getNiveau().name());
        labelDateCreation.setText(u.getDateCreation() == null ? "-"
                : u.getDateCreation().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
    }

    @FXML
    private void changerMotDePasse() {
        String nouveau = champNouveau.getText();

        try {
            if (nouveau == null || !nouveau.equals(champConfirmation.getText())) {
                labelMessage.setText("La confirmation ne correspond pas au nouveau mot de passe.");
                return;
            }
            compteService.changerMotDePasse(champAncien.getText(), nouveau);
            labelMessage.setText("Mot de passe modifié.");

        } catch (IllegalArgumentException | SecurityException e) {
            labelMessage.setText(e.getMessage());
        } catch (SQLException e) {
            labelMessage.setText("Erreur d'accès à la base : " + e.getMessage());
        } finally {
            champAncien.clear();
            champNouveau.clear();
            champConfirmation.clear();
        }
    }
}

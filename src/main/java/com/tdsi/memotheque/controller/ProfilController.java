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
    @FXML private Label labelTelephone;
    @FXML private Label labelRole;
    @FXML private Label labelNiveau;
    @FXML private Label labelDateCreation;
    @FXML private PasswordField champAncien;
    @FXML private PasswordField champNouveau;
    @FXML private PasswordField champConfirmation;
    @FXML private Label labelMessage;
    @FXML private Label labelObligation;
    @FXML private javafx.scene.control.CheckBox caseThemeSombre;
    @FXML private Label labelDoubleFacteur;
    @FXML private Label pastilleDoubleFacteur;
    @FXML private javafx.scene.control.Button boutonDoubleFacteur;

    private final com.tdsi.memotheque.service.DoubleFacteurService doubleFacteurService =
            new com.tdsi.memotheque.service.DoubleFacteurService();

    private final CompteService compteService = new CompteService();

    @FXML
    private void initialize() {
        caseThemeSombre.setSelected(com.tdsi.memotheque.util.Theme.estSombre());

        Utilisateur u = SessionManager.getUtilisateur();
        if (u == null) {
            labelMessage.setText("Aucun utilisateur connecté.");
            return;
        }
        labelNom.setText(u.getNomComplet());
        labelEmail.setText(u.getEmail());
        labelMatricule.setText(u.getMatricule() == null ? "-" : u.getMatricule());
        labelTelephone.setText(u.getTelephone() == null ? "-" : u.getTelephone());
        labelRole.setText(u.getRole() == null ? "-" : u.getRole().getLibelle());
        labelNiveau.setText(u.getNiveau() == null ? "-" : u.getNiveau().name());
        labelDateCreation.setText(u.getDateCreation() == null ? "-"
                : u.getDateCreation().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));

        boolean obligation = u.isDoitChangerMotDePasse();
        labelObligation.setVisible(obligation);
        labelObligation.setManaged(obligation);
        majDoubleFacteur();
    }

    // ------------------------------------------------------------------ double authentification

    private void majDoubleFacteur() {
        Utilisateur u = SessionManager.getUtilisateur();
        boolean active;
        try {
            active = doubleFacteurService.estActive(u.getId());
        } catch (SQLException e) {
            labelDoubleFacteur.setText("État indisponible : " + e.getMessage());
            return;
        }
        pastilleDoubleFacteur.setText(active ? "Activée" : "Désactivée");
        pastilleDoubleFacteur.getStyleClass().removeAll("valide", "neutre", "en_attente");
        pastilleDoubleFacteur.getStyleClass().add(active ? "valide" : u.getRole() == com.tdsi.memotheque.model.Role.ADMIN
                ? "en_attente" : "neutre");
        labelDoubleFacteur.setText(active
                ? "À chaque connexion, un code à 6 chiffres de votre application d'authentification est demandé après le mot de passe."
                : "Protégez votre compte même si votre mot de passe fuit : un code à 6 chiffres (Google Authenticator, "
                        + "Microsoft Authenticator, Aegis...) sera demandé à chaque connexion."
                        + (u.getRole() == com.tdsi.memotheque.model.Role.ADMIN ? " Fortement recommandé pour la scolarité." : ""));
        boutonDoubleFacteur.setText(active ? "Désactiver..." : "Activer...");
        boutonDoubleFacteur.getStyleClass().remove("bouton-principal");
        if (!active) {
            boutonDoubleFacteur.getStyleClass().add("bouton-principal");
        }
    }

    @FXML
    private void doubleFacteur() {
        try {
            if (doubleFacteurService.estActive(SessionManager.getUtilisateur().getId())) {
                dialogueDesactivation();
            } else {
                dialogueActivation();
            }
        } catch (SQLException e) {
            labelMessage.setText(e.getMessage());
        }
        majDoubleFacteur();
    }

    /** QR code otpauth:// à scanner, clé lisible pour la saisie manuelle, puis code de confirmation. */
    private void dialogueActivation() {
        com.tdsi.memotheque.service.DoubleFacteurService.Enrolement e = doubleFacteurService.preparer();
        javafx.scene.image.ImageView qr = com.tdsi.memotheque.util.QrCode.encoder(e.uri()).vue(230);
        Label etape1 = new Label("1. Dans votre application d'authentification, ajoutez un compte en scannant ce QR code.");
        Label etape2 = new Label("Sans appareil photo : saisissez la clé ci-dessous (type « basé sur le temps »).");
        javafx.scene.control.TextField cle = new javafx.scene.control.TextField(e.secretLisible());
        cle.setEditable(false);
        cle.setStyle("-fx-font-family: monospace; -fx-font-size: 15px;");
        Label etape3 = new Label("2. Saisissez le code à 6 chiffres affiché par l'application pour confirmer :");
        javafx.scene.control.TextField code = new javafx.scene.control.TextField();
        code.setPromptText("123 456");
        code.setMaxWidth(180);
        code.setStyle("-fx-font-size: 18px;");
        Label erreur = new Label();
        erreur.getStyleClass().add("message-erreur");
        for (Label l : new Label[] {etape1, etape2, etape3, erreur}) {
            l.setWrapText(true);
            l.setMaxWidth(440);
        }
        javafx.scene.layout.VBox boite = new javafx.scene.layout.VBox(10, etape1, qr, etape2, cle, etape3, code, erreur);
        boite.setAlignment(javafx.geometry.Pos.TOP_LEFT);

        javafx.scene.control.Dialog<javafx.scene.control.ButtonType> d = new javafx.scene.control.Dialog<>();
        d.setTitle("Activer la double authentification");
        d.getDialogPane().setContent(boite);
        javafx.scene.control.ButtonType activer = new javafx.scene.control.ButtonType("Activer",
                javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(activer, javafx.scene.control.ButtonType.CANCEL);
        d.getDialogPane().lookupButton(activer).addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            try {
                doubleFacteurService.activer(e, code.getText());
            } catch (IllegalArgumentException | SecurityException | SQLException ex) {
                erreur.setText(ex.getMessage());
                ev.consume();
            }
        });
        javafx.application.Platform.runLater(code::requestFocus);
        if (d.showAndWait().filter(b -> b == activer).isPresent()) {
            labelMessage.setText("Double authentification activée. Gardez votre téléphone : en cas de perte, "
                    + "la scolarité peut la retirer de votre compte.");
        }
        java.util.Arrays.fill(e.secret(), (byte) 0);
    }

    private void dialogueDesactivation() {
        PasswordField mdp = new PasswordField();
        mdp.setPromptText("Votre mot de passe");
        Label erreur = new Label();
        erreur.getStyleClass().add("message-erreur");
        javafx.scene.layout.VBox boite = new javafx.scene.layout.VBox(10,
                new Label("Confirmez votre mot de passe pour désactiver la double authentification."), mdp, erreur);
        javafx.scene.control.Dialog<javafx.scene.control.ButtonType> d = new javafx.scene.control.Dialog<>();
        d.setTitle("Désactiver la double authentification");
        d.getDialogPane().setContent(boite);
        d.getDialogPane().getButtonTypes().addAll(javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);
        d.getDialogPane().lookupButton(javafx.scene.control.ButtonType.OK).addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            try {
                doubleFacteurService.desactiver(mdp.getText());
            } catch (IllegalArgumentException | SecurityException | SQLException ex) {
                erreur.setText(ex.getMessage());
                ev.consume();
            }
        });
        if (d.showAndWait().filter(b -> b == javafx.scene.control.ButtonType.OK).isPresent()) {
            labelMessage.setText("Double authentification désactivée.");
        }
    }

    @FXML
    private void changerTheme() {
        com.tdsi.memotheque.util.Theme.choisirSombre(caseThemeSombre.isSelected(), caseThemeSombre.getScene());
    }

    @FXML
    private void changerMotDePasse() {
        String nouveau = champNouveau.getText();

        try {
            if (nouveau == null || !nouveau.equals(champConfirmation.getText())) {
                labelMessage.setText("La confirmation ne correspond pas au nouveau mot de passe.");
                return;
            }
            boolean etaitTemporaire = SessionManager.getUtilisateur() != null
                    && SessionManager.getUtilisateur().isDoitChangerMotDePasse();
            compteService.changerMotDePasse(champAncien.getText(), nouveau);
            labelMessage.setText("Mot de passe modifié.");
            if (etaitTemporaire) {
                // Les menus étaient bloqués : on recharge le cadre principal complet.
                com.tdsi.memotheque.util.AlertUtil.info("Mot de passe", "Mot de passe modifié. Bienvenue !");
                com.tdsi.memotheque.util.SceneNavigator.afficherCadrePrincipal();
            }

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

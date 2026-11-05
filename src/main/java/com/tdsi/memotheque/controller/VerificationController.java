package com.tdsi.memotheque.controller;

import java.sql.SQLException;

import com.tdsi.memotheque.service.VerificationService;
import com.tdsi.memotheque.util.SceneNavigator;
import com.tdsi.memotheque.util.SessionManager;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Écran « Vérifier un document » : QR code collé ou code saisi ; accessible sans être connecté. */
public class VerificationController {

    @FXML private HBox barreRetour;
    @FXML private TextArea zoneSaisie;
    @FXML private VBox carteResultat;
    @FXML private Label labelEtat;
    @FXML private Label labelType;
    @FXML private Label labelMessage;
    @FXML private GridPane grilleChamps;
    @FXML private Label labelEmission;
    @FXML private TextArea zoneCle;
    @FXML private Label labelEmpreinte;

    private final VerificationService service = new VerificationService();

    @FXML
    private void initialize() {
        boolean public_ = !SessionManager.estConnecte();
        barreRetour.setVisible(public_);
        barreRetour.setManaged(public_);
        try {
            zoneCle.setText(service.clePubliqueBase64());
            labelEmpreinte.setText("Empreinte SHA-256 de la clé : " + service.empreinteCle());
        } catch (SQLException e) {
            zoneCle.setText("Clé indisponible : " + e.getMessage());
        }
    }

    @FXML
    private void verifier() {
        try {
            afficher(service.verifier(zoneSaisie.getText()));
        } catch (SQLException | RuntimeException e) {
            carteResultat.setVisible(true);
            carteResultat.setManaged(true);
            labelEtat.setText("Erreur");
            labelMessage.setText("Vérification impossible : " + e.getMessage());
        }
    }

    private void afficher(VerificationService.Resultat r) {
        carteResultat.setVisible(true);
        carteResultat.setManaged(true);
        labelEtat.setText(r.etat().libelle);
        labelEtat.getStyleClass().removeAll("valide", "refuse", "en_attente", "neutre");
        labelEtat.getStyleClass().add(switch (r.etat()) {
            case AUTHENTIQUE -> "valide";
            case PERIME -> "en_attente";
            case INVALIDE, REVOQUE -> "refuse";
            case INCONNU -> "neutre";
        });
        labelType.setText(r.typeLibelle() == null ? "" : r.typeLibelle());
        labelMessage.setText(r.message());
        grilleChamps.getChildren().clear();
        for (int i = 0; i < r.champs().size(); i++) {
            Label k = new Label(r.champs().get(i)[0]);
            k.getStyleClass().add("champ-titre");
            k.setMinWidth(Label.USE_PREF_SIZE);
            Label v = new Label(r.champs().get(i)[1]);
            v.setWrapText(true);
            grilleChamps.addRow(i, k, v);
        }
        labelEmission.setText((r.code() == null ? "" : "Code " + r.code())
                + (r.emisLe() == null ? "" : " · émis le " + r.emisLe())
                + " · clé de l'établissement " + r.empreinteCle());
    }

    @FXML
    private void coller() {
        String t = Clipboard.getSystemClipboard().getString();
        if (t != null) {
            zoneSaisie.setText(t.trim());
        }
    }

    @FXML
    private void effacer() {
        zoneSaisie.clear();
        carteResultat.setVisible(false);
        carteResultat.setManaged(false);
    }

    @FXML
    private void retour() {
        SceneNavigator.afficherLogin();
    }
}

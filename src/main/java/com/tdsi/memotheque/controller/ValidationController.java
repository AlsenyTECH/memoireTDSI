package com.tdsi.memotheque.controller;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;

import com.tdsi.memotheque.model.Memoire;
import com.tdsi.memotheque.service.ValidationService;
import com.tdsi.memotheque.util.AlertUtil;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.cell.PropertyValueFactory;

/** [M5] Mémoires à valider : boutons Valider / Refuser (motif obligatoire). */
public class ValidationController {

    @FXML private TableView<Memoire> tableMemoires;
    @FXML private TableColumn<Memoire, String> colTitre;
    @FXML private TableColumn<Memoire, String> colAuteur;
    @FXML private TableColumn<Memoire, Object> colNiveau;
    @FXML private TableColumn<Memoire, String> colAnnee;
    @FXML private TableColumn<Memoire, String> colDomaine;
    @FXML private TableColumn<Memoire, String> colEncadreur;
    @FXML private TableColumn<Memoire, Object> colDateDepot;
    @FXML private Label labelDetail;
    @FXML private TextArea zoneMotif;
    @FXML private Button boutonOuvrir;
    @FXML private Button boutonValider;
    @FXML private Button boutonRefuser;
    @FXML private Label labelMessage;

    private final ValidationService validationService = new ValidationService();
    private final ObservableList<Memoire> memoires = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        colTitre.setCellValueFactory(new PropertyValueFactory<>("titre"));
        colAuteur.setCellValueFactory(new PropertyValueFactory<>("auteurNomComplet"));
        colNiveau.setCellValueFactory(new PropertyValueFactory<>("niveau"));
        colAnnee.setCellValueFactory(new PropertyValueFactory<>("anneeAcademique"));
        colDomaine.setCellValueFactory(new PropertyValueFactory<>("domaineNom"));
        colEncadreur.setCellValueFactory(new PropertyValueFactory<>("encadreurNomComplet"));
        colDateDepot.setCellValueFactory(new PropertyValueFactory<>("dateDepot"));

        tableMemoires.setItems(memoires);
        tableMemoires.getSelectionModel().selectedItemProperty()
                .addListener((obs, ancien, nouveau) -> afficherSelection(nouveau));
        afficherSelection(null);

        charger();
    }

    private void charger() {
        try {
            memoires.setAll(validationService.listerEnAttente());
        } catch (SQLException | SecurityException e) {
            labelMessage.setText("Impossible de charger les mémoires : " + e.getMessage());
        }
    }

    private void afficherSelection(Memoire m) {
        boolean aucun = m == null;
        boutonOuvrir.setDisable(aucun);
        boutonValider.setDisable(aucun);
        boutonRefuser.setDisable(aucun);
        zoneMotif.clear();

        if (aucun) {
            labelDetail.setText(memoires.isEmpty()
                    ? "Aucun mémoire en attente de validation."
                    : "Sélectionnez un mémoire dans la liste.");
            return;
        }

        labelDetail.setText("Résumé : " + m.getResume()
                + "\n\nMots-clés : " + m.getMotsCles()
                + (m.getCoAuteur() == null ? "" : "\nCo-auteur : " + m.getCoAuteur())
                + "\nFichier : " + m.getNomOriginal() + " (" + (m.getTailleOctets() / 1024) + " Ko)"
                + "\nSHA-256 : " + m.getEmpreinteSha256());
    }

    @FXML
    private void ouvrirPdf() {
        Memoire m = tableMemoires.getSelectionModel().getSelectedItem();
        if (m == null) {
            return;
        }
        try {
            Path fichier = validationService.preparerLecture(m.getId());

            if (!Desktop.isDesktopSupported()
                    || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                labelMessage.setText("L'ouverture des PDF n'est pas prise en charge sur ce poste.");
                return;
            }
            Desktop.getDesktop().open(fichier.toFile());
            labelMessage.setText("");

        } catch (IllegalArgumentException | IllegalStateException | SecurityException e) {
            labelMessage.setText(e.getMessage());
        } catch (SQLException | IOException e) {
            labelMessage.setText("Impossible d'ouvrir le PDF : " + e.getMessage());
        }
    }

    @FXML
    private void valider() {
        Memoire m = tableMemoires.getSelectionModel().getSelectedItem();
        if (m == null || !AlertUtil.confirmer("Validation",
                "Valider et publier le mémoire « " + m.getTitre() + " » ?")) {
            return;
        }
        try {
            validationService.valider(m.getId());
            charger();
            labelMessage.setText("Mémoire validé et publié.");
        } catch (IllegalArgumentException | IllegalStateException | SecurityException e) {
            labelMessage.setText(e.getMessage());
        } catch (SQLException | IOException e) {
            labelMessage.setText("Erreur lors de la validation : " + e.getMessage());
        }
    }

    @FXML
    private void refuser() {
        Memoire m = tableMemoires.getSelectionModel().getSelectedItem();
        if (m == null) {
            return;
        }
        try {
            validationService.refuser(m.getId(), zoneMotif.getText());
            charger();
            labelMessage.setText("Mémoire refusé. L'étudiant verra le motif dans « Mes mémoires ».");
        } catch (IllegalArgumentException | IllegalStateException | SecurityException e) {
            labelMessage.setText(e.getMessage());
        } catch (SQLException e) {
            labelMessage.setText("Erreur lors du refus : " + e.getMessage());
        }
    }
}

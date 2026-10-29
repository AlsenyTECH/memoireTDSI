package com.tdsi.memotheque.controller;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import com.tdsi.memotheque.model.JournalActivite;
import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.service.CompteService;
import com.tdsi.memotheque.service.JournalService;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

/** [M6] (Admin) Affichage du journal d'activité. */
public class JournalController {

    private static final int LIMITE = 200;
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    @FXML private TableView<JournalActivite> tableJournal;
    @FXML private TableColumn<JournalActivite, String> colDate;
    @FXML private TableColumn<JournalActivite, String> colUtilisateur;
    @FXML private TableColumn<JournalActivite, String> colAction;
    @FXML private TableColumn<JournalActivite, String> colDetails;
    @FXML private TextField champFiltre;
    @FXML private Label labelMessage;
    @FXML private Label labelIntegrite;

    private final JournalService journalService = new JournalService();
    private final CompteService compteService = new CompteService();
    private final ObservableList<JournalActivite> lignes = FXCollections.observableArrayList();
    private final FilteredList<JournalActivite> lignesFiltrees = new FilteredList<>(lignes, j -> true);
    private final Map<Integer, String> nomsUtilisateurs = new HashMap<>();

    @FXML
    private void initialize() {
        colDate.setCellValueFactory(c -> new SimpleStringProperty(formater(c.getValue().getDateHeure())));
        colUtilisateur.setCellValueFactory(c -> new SimpleStringProperty(nom(c.getValue().getIdUtilisateur())));
        colAction.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getAction()));
        colDetails.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDetails()));

        tableJournal.setItems(lignesFiltrees);
        champFiltre.textProperty().addListener((obs, ancien, texte) -> filtrer(texte));

        actualiser();
    }

    @FXML
    private void actualiser() {
        try {
            nomsUtilisateurs.clear();
            for (Utilisateur u : compteService.listerComptes()) {
                nomsUtilisateurs.put(u.getId(), u.getNomComplet() + " <" + u.getEmail() + ">");
            }
            lignes.setAll(journalService.derniers(LIMITE));
            labelMessage.setText(lignes.size() + " entrée(s) affichée(s) (les " + LIMITE + " plus récentes).");
        } catch (SQLException | SecurityException e) {
            labelMessage.setText("Impossible de charger le journal : " + e.getMessage());
        }
    }

    @FXML
    private void verifierIntegrite() {
        try {
            com.tdsi.memotheque.model.VerificationJournal v = journalService.verifierIntegrite();
            labelIntegrite.setText((v.intact() ? "✔ " : "✖ ") + v.message());
            labelIntegrite.getStyleClass().setAll(v.intact() ? "bandeau-succes" : "bandeau-danger");
            labelIntegrite.setVisible(true);
            labelIntegrite.setManaged(true);
            actualiser();
        } catch (SQLException | SecurityException | IllegalStateException e) {
            labelMessage.setText("Vérification impossible : " + e.getMessage());
        }
    }

    @FXML
    private void exporterCsv() {
        javafx.stage.FileChooser choix = new javafx.stage.FileChooser();
        choix.setTitle("Exporter le journal");
        choix.setInitialFileName("journal-memotdsi.csv");
        choix.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("CSV", "*.csv"));
        java.io.File fichier = choix.showSaveDialog(tableJournal.getScene().getWindow());
        if (fichier == null) {
            return;
        }
        java.util.List<Object[]> lignesCsv = new java.util.ArrayList<>();
        for (JournalActivite j : lignesFiltrees) {
            lignesCsv.add(new Object[] {formater(j.getDateHeure()), nom(j.getIdUtilisateur()),
                    j.getAction(), j.getDetails(), j.getHmac()});
        }
        try {
            com.tdsi.memotheque.util.CsvUtil.ecrire(fichier.toPath(),
                    new Object[] {"Date", "Utilisateur", "Action", "Détails", "HMAC"}, lignesCsv);
            labelMessage.setText(lignesCsv.size() + " ligne(s) exportée(s) dans " + fichier.getName());
        } catch (java.io.IOException e) {
            labelMessage.setText("Export impossible : " + e.getMessage());
        }
    }

    private void filtrer(String texte) {
        String f = texte == null ? "" : texte.trim().toLowerCase();
        lignesFiltrees.setPredicate(j -> f.isEmpty()
                || contient(j.getAction(), f)
                || contient(j.getDetails(), f)
                || contient(nom(j.getIdUtilisateur()), f));
    }

    private boolean contient(String valeur, String filtre) {
        return valeur != null && valeur.toLowerCase().contains(filtre);
    }

    private String nom(Integer id) {
        if (id == null) {
            return "(inconnu)";
        }
        return nomsUtilisateurs.getOrDefault(id, "Utilisateur #" + id);
    }

    private String formater(LocalDateTime date) {
        return date == null ? "" : date.format(FORMAT);
    }
}

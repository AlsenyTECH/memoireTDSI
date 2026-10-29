package com.tdsi.memotheque.controller;

import java.io.IOException;
import java.sql.SQLException;

import com.tdsi.memotheque.model.Memoire;
import com.tdsi.memotheque.model.StatutMemoire;
import com.tdsi.memotheque.service.MemoireService;
import com.tdsi.memotheque.util.AlertUtil;
import com.tdsi.memotheque.util.SceneNavigator;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

/** [M3] « Mes mémoires » : tableau avec statut en couleur, motif de refus, modification / suppression. */
public class MesMemoiresController {

    @FXML private TableView<Memoire> tableMemoires;
    @FXML private TableColumn<Memoire, String> colTitre;
    @FXML private TableColumn<Memoire, Object> colNiveau;
    @FXML private TableColumn<Memoire, String> colAnnee;
    @FXML private TableColumn<Memoire, String> colDomaine;
    @FXML private TableColumn<Memoire, Object> colDateDepot;
    @FXML private TableColumn<Memoire, StatutMemoire> colStatut;
    @FXML private TableColumn<Memoire, String> colMotif;
    @FXML private Button boutonModifier;
    @FXML private Button boutonSupprimer;
    @FXML private Button boutonDetail;
    @FXML private Button boutonHistorique;
    @FXML private Label labelMessage;

    private final MemoireService memoireService = new MemoireService();
    private final ObservableList<Memoire> memoires = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        colTitre.setCellValueFactory(new PropertyValueFactory<>("titre"));
        colNiveau.setCellValueFactory(new PropertyValueFactory<>("niveau"));
        colAnnee.setCellValueFactory(new PropertyValueFactory<>("anneeAcademique"));
        colDomaine.setCellValueFactory(new PropertyValueFactory<>("domaineNom"));
        colDateDepot.setCellValueFactory(new PropertyValueFactory<>("dateDepot"));
        colStatut.setCellValueFactory(new PropertyValueFactory<>("statut"));
        colMotif.setCellValueFactory(new PropertyValueFactory<>("motifRefus"));

        colStatut.setCellFactory(colonne -> new TableCell<>() {
            @Override
            protected void updateItem(StatutMemoire statut, boolean vide) {
                super.updateItem(statut, vide);
                getStyleClass().removeAll("statut-en_attente", "statut-valide", "statut-refuse");
                if (vide || statut == null) {
                    setText(null);
                    return;
                }
                setText(statut.getLibelle());
                getStyleClass().add("statut-" + statut.name().toLowerCase());
            }
        });

        tableMemoires.setItems(memoires);
        tableMemoires.getSelectionModel().selectedItemProperty()
                .addListener((obs, ancien, nouveau) -> majBoutons(nouveau));
        majBoutons(null);

        charger();
    }

    private void charger() {
        try {
            memoires.setAll(memoireService.mesMemoires());
            labelMessage.setText(memoires.isEmpty() ? "Vous n'avez encore déposé aucun mémoire." : "");
        } catch (SQLException | SecurityException e) {
            labelMessage.setText("Impossible de charger vos mémoires : " + e.getMessage());
        }
    }

    private void majBoutons(Memoire m) {
        boolean modifiable = m != null && m.getStatut() != StatutMemoire.VALIDE;
        boutonModifier.setDisable(!modifiable);
        boutonSupprimer.setDisable(!modifiable);
        boutonDetail.setDisable(m == null);
        boutonHistorique.setDisable(m == null);
    }

    @FXML
    private void modifier() {
        Memoire m = tableMemoires.getSelectionModel().getSelectedItem();
        if (m == null) {
            return;
        }
        DepotController.preparerModification(m);
        SceneNavigator.charger("depot");
    }

    @FXML
    private void supprimer() {
        Memoire m = tableMemoires.getSelectionModel().getSelectedItem();
        if (m == null) {
            return;
        }
        if (!AlertUtil.confirmer("Suppression",
                "Supprimer définitivement le mémoire « " + m.getTitre() + " » et son fichier ?")) {
            return;
        }
        try {
            memoireService.supprimer(m.getId());
            charger();
            labelMessage.setText("Mémoire supprimé.");
        } catch (IllegalArgumentException | IllegalStateException | SecurityException e) {
            labelMessage.setText(e.getMessage());
        } catch (SQLException | IOException e) {
            labelMessage.setText("Erreur lors de la suppression : " + e.getMessage());
        }
    }

    @FXML
    private void voirDetail() {
        Memoire m = tableMemoires.getSelectionModel().getSelectedItem();
        if (m != null) {
            SceneNavigator.afficherDetailMemoire(m);
        }
    }

    @FXML
    private void voirHistorique() {
        Memoire m = tableMemoires.getSelectionModel().getSelectedItem();
        if (m == null) {
            return;
        }
        try {
            java.util.List<com.tdsi.memotheque.model.MemoireVersion> versions = memoireService.historique(m.getId());
            if (versions.isEmpty()) {
                AlertUtil.info("Historique", "Aucune version précédente : le PDF n'a jamais été remplacé.");
                return;
            }
            AlertUtil.afficherTableau("Historique des versions — " + m.getTitre(),
                    new String[] {"Remplacé le", "Fichier", "Taille", "Statut alors", "Motif du refus", "SHA-256"},
                    versions.stream().map(v -> new String[] {
                            v.getDateArchivage().toLocalDate().toString(), v.getNomOriginal(), v.getTailleLisible(),
                            v.getStatut().getLibelle(), v.getMotifRefus() == null ? "-" : v.getMotifRefus(),
                            v.getEmpreinteSha256().substring(0, 16) + "…"}).toList());
        } catch (SQLException | SecurityException | IllegalArgumentException e) {
            labelMessage.setText("Historique indisponible : " + e.getMessage());
        }
    }

    @FXML
    private void nouveauDepot() {
        SceneNavigator.charger("depot");
    }
}

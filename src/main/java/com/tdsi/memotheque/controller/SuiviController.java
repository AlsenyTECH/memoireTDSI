package com.tdsi.memotheque.controller;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.tdsi.memotheque.model.Memoire;
import com.tdsi.memotheque.model.Role;
import com.tdsi.memotheque.model.Soutenance;
import com.tdsi.memotheque.model.StatutMemoire;
import com.tdsi.memotheque.service.SoutenanceService;
import com.tdsi.memotheque.service.ValidationService;
import com.tdsi.memotheque.util.Libelles;
import com.tdsi.memotheque.util.SceneNavigator;
import com.tdsi.memotheque.util.SessionManager;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

/**
 * Suivi des mémoires à toutes les étapes du circuit.
 * Encadreur : « Mes encadrements ». Administration : tous les mémoires (pilotage de la scolarité).
 */
public class SuiviController {

    private static final String TOUS = "Tous les états";

    @FXML private Label labelTitre;
    @FXML private Label labelResume;
    @FXML private ComboBox<String> comboEtat;
    @FXML private TextField champFiltre;
    @FXML private TableView<Memoire> table;
    @FXML private TableColumn<Memoire, String> colTitre;
    @FXML private TableColumn<Memoire, String> colEtudiant;
    @FXML private TableColumn<Memoire, Object> colNiveau;
    @FXML private TableColumn<Memoire, String> colAnnee;
    @FXML private TableColumn<Memoire, String> colEncadreur;
    @FXML private TableColumn<Memoire, StatutMemoire> colEtat;
    @FXML private TableColumn<Memoire, String> colSoutenance;
    @FXML private TableColumn<Memoire, String> colResultat;
    @FXML private Button boutonDetail;
    @FXML private Label labelMessage;

    private final ValidationService validationService = new ValidationService();
    private final SoutenanceService soutenanceService = new SoutenanceService();
    private final ObservableList<Memoire> memoires = FXCollections.observableArrayList();
    private final FilteredList<Memoire> filtres = new FilteredList<>(memoires);
    private final Map<Integer, Soutenance> soutenances = new HashMap<>();

    @FXML
    private void initialize() {
        boolean admin = SessionManager.aLeRole(Role.ADMIN);
        labelTitre.setText(admin ? "Suivi des mémoires" : "Mes encadrements");
        colEncadreur.setVisible(admin);

        colTitre.setCellValueFactory(new PropertyValueFactory<>("titre"));
        colEtudiant.setCellValueFactory(new PropertyValueFactory<>("auteurNomComplet"));
        colNiveau.setCellValueFactory(new PropertyValueFactory<>("niveau"));
        colAnnee.setCellValueFactory(new PropertyValueFactory<>("anneeAcademique"));
        colEncadreur.setCellValueFactory(new PropertyValueFactory<>("encadreurNomComplet"));
        colEtat.setCellValueFactory(new PropertyValueFactory<>("statut"));
        colSoutenance.setCellValueFactory(c -> new SimpleStringProperty(
                Libelles.soutenance(c.getValue(), soutenances.get(c.getValue().getId()))));
        colResultat.setCellValueFactory(c -> new SimpleStringProperty(Libelles.resultat(c.getValue())));
        colEtat.setCellFactory(colonne -> new TableCell<>() {
            @Override
            protected void updateItem(StatutMemoire statut, boolean vide) {
                super.updateItem(statut, vide);
                getStyleClass().removeAll("statut-en_attente", "statut-valide", "statut-refuse", "statut-autorise");
                if (vide || statut == null) {
                    setText(null);
                    return;
                }
                setText(statut.getLibelle());
                getStyleClass().add("statut-" + statut.name().toLowerCase(Locale.ROOT));
            }
        });

        comboEtat.getItems().add(TOUS);
        for (StatutMemoire s : StatutMemoire.values()) {
            comboEtat.getItems().add(s.getLibelle());
        }
        comboEtat.setValue(TOUS);
        comboEtat.valueProperty().addListener((o, a, b) -> filtrer());
        champFiltre.textProperty().addListener((o, a, b) -> filtrer());

        table.setItems(filtres);
        table.getSelectionModel().selectedItemProperty().addListener((o, a, m) -> boutonDetail.setDisable(m == null));
        table.setRowFactory(t -> {
            javafx.scene.control.TableRow<Memoire> ligne = new javafx.scene.control.TableRow<>();
            ligne.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !ligne.isEmpty()) {
                    SceneNavigator.afficherDetailMemoire(ligne.getItem());
                }
            });
            return ligne;
        });
        boutonDetail.setDisable(true);
        charger();
    }

    private void charger() {
        try {
            soutenances.clear();
            for (Soutenance s : soutenanceService.soutenances()) {
                soutenances.put(s.getIdMemoire(), s);
            }
            memoires.setAll(validationService.suivi());
            resumer();
        } catch (SQLException | SecurityException e) {
            labelMessage.setText("Impossible de charger le suivi : " + e.getMessage());
        }
    }

    /** Compteurs par étape, comme un tableau de pilotage. */
    private void resumer() {
        Map<StatutMemoire, Integer> n = new HashMap<>();
        for (Memoire m : memoires) {
            n.merge(m.getStatut(), 1, Integer::sum);
        }
        labelResume.setText(memoires.size() + " mémoire(s) — "
                + n.getOrDefault(StatutMemoire.EN_ATTENTE, 0) + " en attente d'avis, "
                + n.getOrDefault(StatutMemoire.REFUSE, 0) + " en correction, "
                + n.getOrDefault(StatutMemoire.AUTORISE, 0) + " autorisé(s) à soutenir, "
                + n.getOrDefault(StatutMemoire.VALIDE, 0) + " publié(s).");
    }

    private void filtrer() {
        String etat = comboEtat.getValue();
        String texte = champFiltre.getText() == null ? "" : champFiltre.getText().trim().toLowerCase(Locale.ROOT);
        filtres.setPredicate(m -> (etat == null || TOUS.equals(etat) || m.getStatut().getLibelle().equals(etat))
                && (texte.isEmpty() || (m.getTitre() + " " + m.getAuteurNomComplet() + " " + m.getEncadreurNomComplet())
                        .toLowerCase(Locale.ROOT).contains(texte)));
    }

    @FXML
    private void voirDetail() {
        Memoire m = table.getSelectionModel().getSelectedItem();
        if (m != null) {
            SceneNavigator.afficherDetailMemoire(m);
        }
    }
}

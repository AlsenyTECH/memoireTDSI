package com.tdsi.memotheque.controller;

import java.sql.SQLException;

import com.tdsi.memotheque.model.Domaine;
import com.tdsi.memotheque.service.DomaineService;
import com.tdsi.memotheque.util.AlertUtil;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

/** [M6] (Admin) Gestion des domaines : ajout, modification, suppression si non utilisé. */
public class DomaineController {

    @FXML private TableView<Domaine> tableDomaines;
    @FXML private TableColumn<Domaine, String> colNom;
    @FXML private TableColumn<Domaine, String> colDescription;
    @FXML private TextField champNom;
    @FXML private TextField champDescription;
    @FXML private Button boutonModifier;
    @FXML private Button boutonSupprimer;
    @FXML private Label labelMessage;

    private final DomaineService domaineService = new DomaineService();
    private final ObservableList<Domaine> domaines = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        colNom.setCellValueFactory(new PropertyValueFactory<>("nom"));
        colDescription.setCellValueFactory(new PropertyValueFactory<>("description"));
        tableDomaines.setItems(domaines);

        tableDomaines.getSelectionModel().selectedItemProperty().addListener((obs, ancien, d) -> {
            boutonModifier.setDisable(d == null);
            boutonSupprimer.setDisable(d == null);
            if (d != null) {
                champNom.setText(d.getNom());
                champDescription.setText(d.getDescription());
            }
        });
        boutonModifier.setDisable(true);
        boutonSupprimer.setDisable(true);

        charger();
    }

    private void charger() {
        try {
            domaines.setAll(domaineService.lister());
        } catch (SQLException e) {
            labelMessage.setText("Impossible de charger les domaines : " + e.getMessage());
        }
    }

    @FXML
    private void ajouter() {
        try {
            domaineService.ajouter(champNom.getText(), champDescription.getText());
            viderFormulaire();
            charger();
            labelMessage.setText("Domaine ajouté.");
        } catch (IllegalArgumentException | SecurityException e) {
            labelMessage.setText(e.getMessage());
        } catch (SQLException e) {
            labelMessage.setText("Erreur : " + e.getMessage());
        }
    }

    @FXML
    private void modifier() {
        Domaine d = tableDomaines.getSelectionModel().getSelectedItem();
        if (d == null) {
            return;
        }
        try {
            domaineService.modifier(d.getId(), champNom.getText(), champDescription.getText());
            viderFormulaire();
            charger();
            labelMessage.setText("Domaine modifié.");
        } catch (IllegalArgumentException | SecurityException e) {
            labelMessage.setText(e.getMessage());
        } catch (SQLException e) {
            labelMessage.setText("Erreur : " + e.getMessage());
        }
    }

    @FXML
    private void supprimer() {
        Domaine d = tableDomaines.getSelectionModel().getSelectedItem();
        if (d == null || !AlertUtil.confirmer("Suppression", "Supprimer le domaine « " + d.getNom() + " » ?")) {
            return;
        }
        try {
            domaineService.supprimer(d.getId());
            viderFormulaire();
            charger();
            labelMessage.setText("Domaine supprimé.");
        } catch (IllegalArgumentException | IllegalStateException | SecurityException e) {
            labelMessage.setText(e.getMessage());
        } catch (SQLException e) {
            labelMessage.setText("Erreur : " + e.getMessage());
        }
    }

    @FXML
    private void viderFormulaire() {
        tableDomaines.getSelectionModel().clearSelection();
        champNom.clear();
        champDescription.clear();
    }
}

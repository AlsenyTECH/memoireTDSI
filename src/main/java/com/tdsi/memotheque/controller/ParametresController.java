package com.tdsi.memotheque.controller;

import java.sql.SQLException;
import java.time.LocalDate;

import com.tdsi.memotheque.service.ParametreService;

import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

/** Paramètres de la scolarité : année académique en cours et campagne de dépôt (administration). */
public class ParametresController {

    @FXML private TextField champAnnee;
    @FXML private CheckBox caseOuverte;
    @FXML private DatePicker dateLimite;
    @FXML private Label labelEtat;
    @FXML private Label labelMessage;

    private final ParametreService service = new ParametreService();

    @FXML
    private void initialize() {
        charger();
    }

    private void charger() {
        try {
            ParametreService.Campagne c = service.campagne();
            champAnnee.setText(c.anneeCourante());
            caseOuverte.setSelected(c.ouverte());
            dateLimite.setValue(c.dateLimite());
            labelEtat.setText(c.depotPossible(LocalDate.now())
                    ? "Les étudiants peuvent déposer leur mémoire pour " + c.anneeCourante() + "."
                    : "Les dépôts sont actuellement impossibles (campagne fermée ou date limite dépassée).");
        } catch (SQLException e) {
            labelMessage.setText("Impossible de lire les paramètres : " + e.getMessage());
        }
    }

    @FXML
    private void proposerAnnee() {
        champAnnee.setText(ParametreService.anneeParDefaut(LocalDate.now()));
    }

    @FXML
    private void enregistrer() {
        try {
            service.definir(champAnnee.getText(), caseOuverte.isSelected(), dateLimite.getValue());
            labelMessage.setText("Paramètres enregistrés.");
            charger();
        } catch (IllegalArgumentException | SecurityException e) {
            labelMessage.setText(e.getMessage());
        } catch (SQLException e) {
            labelMessage.setText("Erreur d'enregistrement : " + e.getMessage());
        }
    }
}

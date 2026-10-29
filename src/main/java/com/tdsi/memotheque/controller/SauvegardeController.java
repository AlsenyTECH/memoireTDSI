package com.tdsi.memotheque.controller;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import com.tdsi.memotheque.service.SauvegardeService;
import com.tdsi.memotheque.util.AlertUtil;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

/** (Admin) Sauvegarde et restauration chiffrées, exécutées en tâche de fond. */
public class SauvegardeController {

    @FXML private PasswordField champMotDePasse;
    @FXML private PasswordField champConfirmation;
    @FXML private Button boutonExporter;
    @FXML private Button boutonRestaurer;
    @FXML private ProgressIndicator indicateur;
    @FXML private Label labelResultat;

    private final SauvegardeService sauvegardeService = new SauvegardeService();

    @FXML
    private void exporter() {
        String mdp = champMotDePasse.getText();
        if (mdp == null || !mdp.equals(champConfirmation.getText())) {
            resultat(false, "Les deux mots de passe ne correspondent pas.");
            return;
        }

        FileChooser choix = new FileChooser();
        choix.setTitle("Enregistrer la sauvegarde");
        choix.setInitialFileName("memotdsi-"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + SauvegardeService.EXTENSION);
        choix.getExtensionFilters().add(new FileChooser.ExtensionFilter("Sauvegarde MémoTDSI", "*" + SauvegardeService.EXTENSION));
        File fichier = choix.showSaveDialog(boutonExporter.getScene().getWindow());
        if (fichier == null) {
            return;
        }

        Task<Integer> tache = new Task<>() {
            @Override
            protected Integer call() throws Exception {
                return sauvegardeService.exporter(fichier.toPath(), mdp);
            }
        };
        tache.setOnSucceeded(e -> {
            occupe(false);
            champMotDePasse.clear();
            champConfirmation.clear();
            resultat(true, "Sauvegarde créée : " + fichier.getName() + " (" + tache.getValue() + " fichiers).");
        });
        tache.setOnFailed(e -> {
            occupe(false);
            resultat(false, "Échec de la sauvegarde : " + tache.getException().getMessage());
        });
        occupe(true);
        new Thread(tache, "sauvegarde").start();
    }

    @FXML
    private void restaurer() {
        FileChooser choix = new FileChooser();
        choix.setTitle("Choisir la sauvegarde");
        choix.getExtensionFilters().add(new FileChooser.ExtensionFilter("Sauvegarde MémoTDSI", "*" + SauvegardeService.EXTENSION));
        File archive = choix.showOpenDialog(boutonRestaurer.getScene().getWindow());
        if (archive == null) {
            return;
        }
        Optional<String> mdp = AlertUtil.demanderMotDePasse("Restauration",
                "Mot de passe de la sauvegarde « " + archive.getName() + " »");
        if (mdp.isEmpty()) {
            return;
        }
        DirectoryChooser dossier = new DirectoryChooser();
        dossier.setTitle("Dossier VIDE où extraire la sauvegarde");
        File cible = dossier.showDialog(boutonRestaurer.getScene().getWindow());
        if (cible == null) {
            return;
        }

        Task<SauvegardeService.Restauration> tache = new Task<>() {
            @Override
            protected SauvegardeService.Restauration call() throws Exception {
                return sauvegardeService.restaurer(archive.toPath(), mdp.get(), cible.toPath());
            }
        };
        tache.setOnSucceeded(e -> {
            occupe(false);
            SauvegardeService.Restauration r = tache.getValue();
            Path d = r.dossier();
            resultat(true, "Sauvegarde du " + r.dateSauvegarde() + " vérifiée et extraite (" + r.fichiers()
                    + " fichiers) dans " + d + ".\nPour l'utiliser : fermez MémoTDSI, puis remplacez memotdsi.db, "
                    + "le dossier storage/ et les fichiers du coffre (dossier coffre/) par ceux extraits.");
        });
        tache.setOnFailed(e -> {
            occupe(false);
            resultat(false, "Restauration refusée : " + tache.getException().getMessage());
        });
        occupe(true);
        new Thread(tache, "restauration").start();
    }

    private void occupe(boolean occupe) {
        indicateur.setVisible(occupe);
        boutonExporter.setDisable(occupe);
        boutonRestaurer.setDisable(occupe);
    }

    private void resultat(boolean succes, String message) {
        labelResultat.setText((succes ? "✔ " : "✖ ") + message);
        labelResultat.getStyleClass().setAll("label", succes ? "bandeau-succes" : "bandeau-danger");
        labelResultat.setVisible(true);
        labelResultat.setManaged(true);
    }
}

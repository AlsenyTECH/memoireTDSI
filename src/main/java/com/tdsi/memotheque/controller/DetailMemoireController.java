
package com.tdsi.memotheque.controller;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;

import com.tdsi.memotheque.service.RechercheService;

import javafx.fxml.FXML;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.time.format.DateTimeFormatter;

import com.tdsi.memotheque.model.Memoire;


import javafx.scene.control.Label;
import javafx.scene.control.TextArea;

public class DetailMemoireController {

    @FXML private Label labelTitre;
    @FXML private Label labelAuteur;
    @FXML private Label labelCoAuteur;
    @FXML private Label labelDomaine;
    @FXML private Label labelNiveau;
    @FXML private Label labelAnnee;
    @FXML private Label labelEncadreur;
    @FXML private Label labelDateSoutenance;
    @FXML private Label labelMention;
    @FXML private Label labelDatePublication;
    @FXML private TextArea zoneResume;
    @FXML private Label labelMotsCles;
    @FXML private Label labelVues;
    @FXML private Label labelTelechargements;
    @FXML private Label labelMessage;
    @FXML private Label labelSignature;

    private Memoire memoire;
    
    private final RechercheService rechercheService = new RechercheService();

    /**
     * Affiche les informations du mémoire sélectionné.
     */
    public void afficherMemoire(Memoire memoire) {
        if (memoire == null) {
            afficherMessage("Aucun mémoire à afficher.");
            return;
        }

        this.memoire = memoire;

        labelTitre.setText(valeurOuTiret(memoire.getTitre()));
        labelAuteur.setText(memoire.getAuteur() == null
                ? "-" : valeurOuTiret(memoire.getAuteur().getNomComplet()));
        labelCoAuteur.setText(valeurOuTiret(memoire.getCoAuteur()));
        labelDomaine.setText(memoire.getDomaineNom());
        labelNiveau.setText(memoire.getNiveau() == null
                ? "-" : memoire.getNiveau().toString());
        labelAnnee.setText(valeurOuTiret(memoire.getAnneeAcademique()));
        labelEncadreur.setText(memoire.getEncadreurNomComplet());
        labelDateSoutenance.setText(memoire.getDateSoutenance() == null
                ? "-" : memoire.getDateSoutenance().format(
                        DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        labelMention.setText(memoire.getMention() == null
                ? "-" : memoire.getMention().toString());
        labelDatePublication.setText(memoire.getDatePublication() == null
                ? "-" : memoire.getDatePublication().format(
                        DateTimeFormatter.ofPattern("dd/MM/yyyy")));

        zoneResume.setText(valeurOuTiret(memoire.getResume()));
        labelMotsCles.setText(valeurOuTiret(memoire.getMotsCles()));
        labelVues.setText("Vues : " + memoire.getNbVues());
        labelTelechargements.setText(
                "Téléchargements : " + memoire.getNbTelechargements());

        afficherMessage("");
        afficherSignature(memoire);
    }

    /** Vérifie la signature Ed25519 en relisant le mémoire en base. */
    private void afficherSignature(Memoire memoire) {
        try {
            com.tdsi.memotheque.service.SignatureService.Verification v =
                    new com.tdsi.memotheque.service.SignatureService().verifier(memoire.getId());
            String style = switch (v.etat()) {
                case VALIDE -> "bandeau-succes";
                case INVALIDE -> "bandeau-danger";
                case ABSENTE -> "bandeau-alerte";
            };
            String icone = switch (v.etat()) {
                case VALIDE -> "✔ ";
                case INVALIDE -> "✖ ";
                case ABSENTE -> "ⓘ ";
            };
            labelSignature.setText(icone + v.message());
            labelSignature.getStyleClass().setAll("label", style);
        } catch (SQLException | IllegalArgumentException | IllegalStateException e) {
            labelSignature.setText("Vérification de la signature impossible : " + e.getMessage());
            labelSignature.getStyleClass().setAll("label", "bandeau-alerte");
        }
    }

    private String valeurOuTiret(String valeur) {
        return valeur == null || valeur.isBlank() ? "-" : valeur;
    }

    private void afficherMessage(String message) {
        labelMessage.setText(message);
    }

    @FXML
    private void ouvrirPdf() {
        try {
            Path fichier = rechercheService.preparerConsultation(memoire);

            if (!Desktop.isDesktopSupported()
                    || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                afficherMessage("L'ouverture des PDF n'est pas prise en charge.");
                return;
            }

            Desktop.getDesktop().open(fichier.toFile());
            afficherMessage("");

        } catch (IOException | SQLException | SecurityException e) {
            afficherMessage("Impossible d'ouvrir le PDF : " + e.getMessage());
        } catch (IllegalArgumentException e) {
            afficherMessage(e.getMessage());
        }
    }

    @FXML
    private void enregistrerSous() {
        try {
        	Path fichierSource = rechercheService.verifierPdfPourTelechargement(memoire);

            FileChooser choixFichier = new FileChooser();
            choixFichier.setTitle("Enregistrer le mémoire");
            choixFichier.setInitialFileName(fichierSource.getFileName().toString());
            choixFichier.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Fichier PDF", "*.pdf")
            );

            Window fenetre = labelMessage.getScene().getWindow();
            java.io.File destination = choixFichier.showSaveDialog(fenetre);

            if (destination == null) {
                afficherMessage("Enregistrement annulé.");
                return;
            }

            Path cheminDestination = destination.toPath()
                    .toAbsolutePath()
                    .normalize();

            if (cheminDestination.equals(fichierSource)) {
                afficherMessage("Choisis un emplacement différent du fichier source.");
                return;
            }

            Files.copy(
                    fichierSource,
                    cheminDestination,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
            );
            
            rechercheService.enregistrerTelechargement(memoire.getId());

            afficherMessage("Mémoire enregistré avec succès.");

        } catch (IOException | SQLException | SecurityException e) {
            afficherMessage("Impossible d'enregistrer le PDF : " + e.getMessage());
        } catch (IllegalArgumentException e) {
            afficherMessage(e.getMessage());
        }
    }
    
    /** Références APA et BibTeX, copiables dans le presse-papiers. */
    @FXML
    private void citer() {
        if (memoire == null) {
            return;
        }
        String apa = com.tdsi.memotheque.util.CitationUtil.apa(memoire);
        String bibtex = com.tdsi.memotheque.util.CitationUtil.bibtex(memoire);

        TextArea zoneApa = new TextArea(apa);
        zoneApa.setEditable(false);
        zoneApa.setWrapText(true);
        zoneApa.setPrefRowCount(3);
        TextArea zoneBib = new TextArea(bibtex);
        zoneBib.setEditable(false);
        zoneBib.setPrefRowCount(10);
        zoneBib.setStyle("-fx-font-family: monospace;");

        javafx.scene.control.Button copierApa = new javafx.scene.control.Button("Copier APA");
        copierApa.setOnAction(e -> copier(apa));
        javafx.scene.control.Button copierBib = new javafx.scene.control.Button("Copier BibTeX");
        copierBib.setOnAction(e -> copier(bibtex));

        javafx.scene.layout.VBox contenu = new javafx.scene.layout.VBox(8,
                new Label("APA 7"), zoneApa, copierApa, new Label("BibTeX"), zoneBib, copierBib);
        contenu.setPrefWidth(620);

        javafx.scene.control.Alert a = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
        a.setTitle("Citer ce mémoire");
        a.setHeaderText(memoire.getTitre());
        a.getDialogPane().setContent(contenu);
        a.showAndWait();
    }

    private void copier(String texte) {
        javafx.scene.input.ClipboardContent c = new javafx.scene.input.ClipboardContent();
        c.putString(texte);
        javafx.scene.input.Clipboard.getSystemClipboard().setContent(c);
    }

    @FXML
    private void retourRecherche() {
        com.tdsi.memotheque.util.SceneNavigator.charger("recherche");
    }
}

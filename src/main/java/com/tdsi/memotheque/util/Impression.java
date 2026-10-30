package com.tdsi.memotheque.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import javafx.geometry.Insets;
import javafx.print.PrinterJob;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/**
 * Documents imprimables (attestation de dépôt, convocation, procès-verbal) :
 * aperçu dans une boîte de dialogue puis impression JavaFX (PrinterJob).
 * Sous Windows, « Microsoft Print to PDF » permet d'en garder un PDF.
 */
public final class Impression {

    private static final DateTimeFormatter HORODATAGE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private Impression() { }

    /** Construit la page : en-tête de l'établissement, titre, tableau libellé/valeur, mention de bas de page. */
    public static VBox document(String titre, List<String[]> lignes, String basDePage) {
        Label etablissement = new Label("Département TDSI — Mémothèque");
        etablissement.setStyle("-fx-font-size: 11px; -fx-text-fill: #555;");
        Label entete = new Label(titre);
        entete.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        GridPane grille = new GridPane();
        grille.setHgap(14);
        grille.setVgap(6);
        for (int i = 0; i < lignes.size(); i++) {
            Label libelle = new Label(lignes.get(i)[0]);
            libelle.setStyle("-fx-font-weight: bold;");
            libelle.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
            Label valeur = new Label(lignes.get(i)[1] == null ? "-" : lignes.get(i)[1]);
            valeur.setWrapText(true);
            valeur.setMaxWidth(380);
            grille.add(libelle, 0, i);
            grille.add(valeur, 1, i);
        }

        Label pied = new Label((basDePage == null ? "" : basDePage + "\n")
                + "Document édité le " + LocalDateTime.now().format(HORODATAGE) + ".");
        pied.setWrapText(true);
        pied.setMaxWidth(500);
        pied.setStyle("-fx-font-size: 10px; -fx-text-fill: #555;");

        VBox page = new VBox(12, etablissement, entete, new Separator(), grille, new Separator(), pied);
        page.setPadding(new Insets(24));
        page.setStyle("-fx-background-color: white;");
        page.setPrefWidth(540);
        return page;
    }

    /** Aperçu puis impression. Retourne false si l'utilisateur annule ou si aucune imprimante n'existe. */
    public static boolean apercuEtImpression(String titre, List<String[]> lignes, String basDePage) {
        VBox page = document(titre, lignes, basDePage);
        ButtonType imprimer = new ButtonType("Imprimer...", ButtonBar.ButtonData.OK_DONE);
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle(titre);
        d.getDialogPane().setContent(page);
        d.getDialogPane().getButtonTypes().addAll(imprimer, ButtonType.CLOSE);
        if (d.showAndWait().filter(b -> b == imprimer).isEmpty()) {
            return false;
        }
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            AlertUtil.avertissement("Impression", "Aucune imprimante n'est configurée sur ce poste.");
            return false;
        }
        // La page doit être hors de la boîte de dialogue (fermée) pour être imprimée seule.
        d.getDialogPane().setContent(null);
        if (!job.showPrintDialog(null)) {
            job.cancelJob();
            return false;
        }
        boolean ok = job.printPage(page);
        job.endJob();
        return ok;
    }
}

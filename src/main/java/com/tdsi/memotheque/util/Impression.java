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

    /** « Fermer » en français, quelle que soit la langue du système. */
    private static final ButtonType FERMER = new ButtonType("Fermer", ButtonBar.ButtonData.CANCEL_CLOSE);

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
        d.getDialogPane().getButtonTypes().addAll(imprimer, FERMER);
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

    // ------------------------------------------------------------------ convocations

    /** Lettre de convocation au format page (en-tête, références, corps, détails, signature). */
    public static VBox lettre(com.tdsi.memotheque.service.ConvocationService.Convocation c) {
        Label etab = new Label("UNIVERSITÉ CHEIKH ANTA DIOP DE DAKAR");
        etab.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        Label service = new Label("Filière TDSI — Service de la scolarité");
        service.getStyleClass().add("lettre-entete");
        VBox gauche = new VBox(2, etab, service);
        javafx.scene.image.ImageView logo = new javafx.scene.image.ImageView(Images.logo(128));
        logo.setFitWidth(46);
        logo.setFitHeight(46);
        javafx.scene.layout.HBox entete = new javafx.scene.layout.HBox(12, logo, gauche);
        entete.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Label lieu = new Label(c.lieuDate());
        Label ref = new Label(c.reference());
        ref.getStyleClass().add("lettre-entete");
        VBox droite = new VBox(2, lieu, ref);
        droite.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        javafx.scene.layout.Region vide = new javafx.scene.layout.Region();
        javafx.scene.layout.HBox.setHgrow(vide, javafx.scene.layout.Priority.ALWAYS);
        javafx.scene.layout.HBox ligneRef = new javafx.scene.layout.HBox(vide, droite);

        Label a = new Label("À l'attention de " + c.destinataire() + " — " + c.qualite());
        a.getStyleClass().add("lettre-libelle");
        Label titre = new Label("CONVOCATION");
        titre.getStyleClass().add("lettre-titre");
        Label objet = new Label("Objet : " + c.objet());
        objet.getStyleClass().add("lettre-objet");

        VBox corps = new VBox(10);
        corps.getChildren().add(new Label(c.formuleAppel()));
        for (String p : c.paragraphes()) {
            Label l = new Label(p);
            l.setWrapText(true);
            corps.getChildren().add(l);
        }

        GridPane grille = new GridPane();
        grille.setHgap(16);
        grille.setVgap(5);
        grille.setStyle("-fx-background-color: #f3f6fb; -fx-padding: 12 16 12 16; -fx-background-radius: 6;");
        for (int i = 0; i < c.details().size(); i++) {
            Label k = new Label(c.details().get(i)[0]);
            k.getStyleClass().add("lettre-libelle");
            k.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
            Label v = new Label(c.details().get(i)[1]);
            v.setWrapText(true);
            grille.add(k, 0, i);
            grille.add(v, 1, i);
        }

        Label fin = new Label(c.formuleFin());
        fin.setWrapText(true);
        Label signature = new Label(c.signature());
        signature.getStyleClass().add("lettre-signature");
        VBox blocSignature = new VBox(signature);
        blocSignature.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        blocSignature.setPadding(new Insets(8, 30, 0, 0));

        Label pied = new Label("Document édité par MémoTDSI le " + LocalDateTime.now().format(HORODATAGE)
                + ". Il ne remplace pas la notification reçue dans l'application.");
        pied.getStyleClass().add("lettre-entete");
        pied.setWrapText(true);

        VBox page = new VBox(entete, new Separator(), ligneRef, a, titre, objet, corps, grille, fin, blocSignature,
                new Separator(), pied);
        page.getStyleClass().add("lettre");
        page.setPrefWidth(620);
        page.setMaxWidth(620);
        return page;
    }

    /** Aperçu (défilant) de plusieurs lettres puis impression d'une page par lettre. */
    public static void apercuEtImpressionLettres(String titre, List<VBox> pages) {
        VBox pile = new VBox(18);
        pile.setPadding(new Insets(16));
        pile.setStyle("-fx-background-color: #d9dee8;");
        for (VBox p : pages) {
            p.setEffect(new javafx.scene.effect.DropShadow(10, javafx.scene.paint.Color.rgb(0, 0, 0, 0.25)));
            pile.getChildren().add(p);
        }
        javafx.scene.control.ScrollPane defile = new javafx.scene.control.ScrollPane(pile);
        defile.setFitToWidth(true);
        defile.setPrefSize(700, 620);

        ButtonType imprimer = new ButtonType("Imprimer " + (pages.size() > 1 ? "les " + pages.size() + " lettres" : "la lettre")
                + "...", ButtonBar.ButtonData.OK_DONE);
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle(titre);
        d.setHeaderText(titre + (pages.size() > 1 ? " — " + pages.size() + " destinataires" : ""));
        d.getDialogPane().setContent(defile);
        d.getDialogPane().getButtonTypes().addAll(imprimer, FERMER);
        if (d.showAndWait().filter(b -> b == imprimer).isEmpty()) {
            return;
        }
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            AlertUtil.avertissement("Impression", "Aucune imprimante n'est configurée sur ce poste.");
            return;
        }
        if (!job.showPrintDialog(null)) {
            job.cancelJob();
            return;
        }
        pile.getChildren().clear();
        for (VBox p : pages) {
            p.setEffect(null);
            javafx.print.PageLayout mise = job.getJobSettings().getPageLayout();
            javafx.scene.Group g = new javafx.scene.Group(p);
            javafx.scene.Scene sc = new javafx.scene.Scene(g);
            java.net.URL css = Impression.class.getResource("/css/style.css");
            if (css != null) {
                sc.getStylesheets().add(css.toExternalForm());
            }
            p.applyCss();
            p.layout();
            double echelle = Math.min(1, Math.min(mise.getPrintableWidth() / p.prefWidth(-1),
                    mise.getPrintableHeight() / p.prefHeight(p.prefWidth(-1))));
            javafx.scene.transform.Scale s = new javafx.scene.transform.Scale(echelle, echelle);
            p.getTransforms().add(s);
            job.printPage(p);
            p.getTransforms().remove(s);
        }
        job.endJob();
    }
}

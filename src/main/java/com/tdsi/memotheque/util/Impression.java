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

    /** Largeur et hauteur d'une lettre (proportions A4). */
    public static final double LETTRE_LARGEUR = 640;
    public static final double LETTRE_HAUTEUR = LETTRE_LARGEUR * 297 / 210;
    /** Largeur du texte (page moins les marges de .lettre). */
    private static final double LETTRE_UTILE = LETTRE_LARGEUR - 92;

    /**
     * Lettre de convocation officielle, au format A4 : en-tête de l'UCAD (sceau, université, filière),
     * références, destinataire, objet, corps, tableau récapitulatif, signature et cachet, pied de page.
     */
    public static VBox lettre(com.tdsi.memotheque.service.ConvocationService.Convocation c) {
        // ---- en-tête : sceau | université | République du Sénégal
        javafx.scene.image.Image sceau = Images.logoUcad();
        javafx.scene.image.ImageView logo = new javafx.scene.image.ImageView(sceau == null ? Images.logo(128) : sceau);
        logo.setFitWidth(86);
        logo.setFitHeight(86);
        logo.setPreserveRatio(true);
        logo.setSmooth(true);

        Label univ = classe(new Label("UNIVERSITÉ CHEIKH ANTA DIOP DE DAKAR"), "lettre-universite");
        Label fac = classe(new Label("Faculté des Sciences et Techniques"), "lettre-faculte");
        Label filiere = classe(new Label("Département de Mathématiques et Informatique"), "lettre-entete");
        Label master = classe(new Label("Master Mathématiques, Cryptographie et Sécurité — TDSI"), "lettre-entete");
        Label scol = classe(new Label("Service de la scolarité"), "lettre-service");
        VBox centre = new VBox(1, univ, fac, filiere, master, scol);
        centre.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        javafx.scene.layout.HBox.setHgrow(centre, javafx.scene.layout.Priority.ALWAYS);
        javafx.scene.layout.HBox entete = new javafx.scene.layout.HBox(18, logo, centre);
        entete.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Label rep = classe(new Label("RÉPUBLIQUE DU SÉNÉGAL"), "lettre-republique");
        Label devise = classe(new Label("Un Peuple — Un But — Une Foi"), "lettre-devise");
        javafx.scene.layout.HBox republique = new javafx.scene.layout.HBox(10, rep, devise);
        republique.setAlignment(javafx.geometry.Pos.CENTER);

        javafx.scene.layout.Region filetBleu = classe(new javafx.scene.layout.Region(), "lettre-filet");
        javafx.scene.layout.Region filetOr = classe(new javafx.scene.layout.Region(), "lettre-filet-or");
        VBox filets = new VBox(2, filetBleu, filetOr);

        // ---- références et date
        Label ref = classe(new Label(c.reference()), "lettre-ref");
        Label lieu = classe(new Label(c.lieuDate()), "lettre-ref");
        javafx.scene.layout.Region r1 = new javafx.scene.layout.Region();
        javafx.scene.layout.HBox.setHgrow(r1, javafx.scene.layout.Priority.ALWAYS);
        javafx.scene.layout.HBox ligneRef = new javafx.scene.layout.HBox(ref, r1, lieu);

        // ---- titre
        Label titre = classe(new Label("CONVOCATION"), "lettre-titre");
        javafx.scene.layout.HBox cadreTitre = new javafx.scene.layout.HBox(titre);
        cadreTitre.setAlignment(javafx.geometry.Pos.CENTER);
        cadreTitre.setPadding(new Insets(6, 0, 2, 0));

        // ---- destinataire (à droite, comme une lettre administrative)
        Label aLAttention = classe(new Label("À l'attention de"), "lettre-petit");
        Label dest = classe(new Label(c.destinataire()), "lettre-destinataire");
        Label qual = classe(new Label(c.qualite()), "lettre-petit");
        VBox blocDest = new VBox(1, aLAttention, dest, qual);
        blocDest.getStyleClass().add("lettre-bloc-destinataire");
        javafx.scene.layout.Region r2 = new javafx.scene.layout.Region();
        javafx.scene.layout.HBox.setHgrow(r2, javafx.scene.layout.Priority.ALWAYS);
        javafx.scene.layout.HBox ligneDest = new javafx.scene.layout.HBox(r2, blocDest);

        Label objetLib = classe(new Label("Objet :"), "lettre-objet-libelle");
        objetLib.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        Label objet = classe(new Label(c.objet()), "lettre-objet");
        enveloppe(objet, LETTRE_UTILE - 70);
        javafx.scene.layout.HBox ligneObjet = new javafx.scene.layout.HBox(8, objetLib, objet);

        // ---- corps
        VBox corps = new VBox(9);
        corps.getChildren().add(new Label(c.formuleAppel()));
        for (String p : c.paragraphes()) {
            Label l = enveloppe(classe(new Label(p), "lettre-paragraphe"), LETTRE_UTILE);
            corps.getChildren().add(l);
        }

        // ---- récapitulatif encadré
        GridPane grille = new GridPane();
        grille.getStyleClass().add("lettre-tableau");
        javafx.scene.layout.ColumnConstraints col1 = new javafx.scene.layout.ColumnConstraints();
        col1.setMinWidth(165);
        col1.setPrefWidth(165);
        javafx.scene.layout.ColumnConstraints col2 = new javafx.scene.layout.ColumnConstraints();
        col2.setHgrow(javafx.scene.layout.Priority.ALWAYS);
        grille.getColumnConstraints().addAll(col1, col2);
        for (int i = 0; i < c.details().size(); i++) {
            Label k = classe(new Label(c.details().get(i)[0]), "lettre-cle");
            Label v = classe(new Label(c.details().get(i)[1]), "lettre-valeur");
            k.setMaxWidth(Double.MAX_VALUE);
            k.setMaxHeight(Double.MAX_VALUE);
            v.setMaxWidth(Double.MAX_VALUE);
            enveloppe(v, LETTRE_UTILE - 168);
            if (i % 2 == 1) {
                v.getStyleClass().add("lettre-valeur-alt");
            }
            grille.add(k, 0, i);
            grille.add(v, 1, i);
        }

        Label fin = enveloppe(classe(new Label(c.formuleFin()), "lettre-paragraphe"), LETTRE_UTILE);

        // ---- signature et cachet
        Label pour = classe(new Label("Pour le Recteur, et par délégation"), "lettre-petit");
        Label signataire = classe(new Label(c.signature()), "lettre-signature");
        javafx.scene.layout.StackPane cachet = new javafx.scene.layout.StackPane(classe(new Label("Cachet\net signature"), "lettre-cachet-texte"));
        cachet.getStyleClass().add("lettre-cachet");
        cachet.setPrefSize(118, 82);
        cachet.setMaxSize(118, 82);
        VBox blocSignature = new VBox(6, pour, signataire, cachet);
        blocSignature.setAlignment(javafx.geometry.Pos.TOP_CENTER);
        javafx.scene.layout.Region r3 = new javafx.scene.layout.Region();
        javafx.scene.layout.HBox.setHgrow(r3, javafx.scene.layout.Priority.ALWAYS);
        javafx.scene.layout.HBox ligneSignature = new javafx.scene.layout.HBox(r3, blocSignature);
        ligneSignature.setPadding(new Insets(4, 10, 0, 0));

        // ---- pied de page, poussé en bas de la feuille
        javafx.scene.layout.Region ressort = new javafx.scene.layout.Region();
        VBox.setVgrow(ressort, javafx.scene.layout.Priority.ALWAYS);
        javafx.scene.layout.Region filetPied = classe(new javafx.scene.layout.Region(), "lettre-filet-fin");
        Label adresse = classe(new Label("Université Cheikh Anta Diop de Dakar — BP 5005 Dakar-Fann, Sénégal — www.ucad.sn"),
                "lettre-pied");
        Label edition = classe(new Label("Document édité par MémoTDSI le " + LocalDateTime.now().format(HORODATAGE)
                + " — référence à rappeler dans toute correspondance."), "lettre-pied-petit");
        VBox pied = new VBox(2, filetPied, adresse, edition);
        pied.setAlignment(javafx.geometry.Pos.CENTER);

        VBox page = new VBox(republique, entete, filets, ligneRef, cadreTitre, ligneDest, ligneObjet, corps, grille, fin,
                ligneSignature, ressort, pied);
        page.getStyleClass().add("lettre");
        // Au moins une feuille A4 ; le pied de page est poussé en bas par le ressort.
        page.setPrefWidth(LETTRE_LARGEUR);
        page.setMinSize(LETTRE_LARGEUR, LETTRE_HAUTEUR);
        page.setMaxWidth(LETTRE_LARGEUR);
        return page;
    }

    /** Texte sur plusieurs lignes, mesuré à sa largeur réelle (sinon il serait tronqué par « ... »). */
    private static Label enveloppe(Label l, double largeur) {
        l.setWrapText(true);
        l.setPrefWidth(largeur);
        l.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        return l;
    }

    private static <T extends javafx.scene.Node> T classe(T n, String classe) {
        n.getStyleClass().add(classe);
        return n;
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

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

    /** Page officielle (attestation, procès-verbal) sans code de vérification. */
    public static VBox document(String titre, List<String[]> lignes, String basDePage) {
        return document(titre, lignes, basDePage, null);
    }

    /**
     * Page officielle A4 : en-tête de l'UCAD, titre, tableau libellé / valeur, mention de bas de page,
     * signature et, si le document est enregistré, son QR code de vérification signé.
     */
    public static VBox document(String titre, List<String[]> lignes, String basDePage,
            com.tdsi.memotheque.model.DocumentEmis verification) {
        Label t = classe(new Label(titre.toUpperCase(java.util.Locale.FRENCH)), "lettre-titre");
        javafx.scene.layout.HBox cadreTitre = new javafx.scene.layout.HBox(t);
        cadreTitre.setAlignment(javafx.geometry.Pos.CENTER);
        cadreTitre.setPadding(new Insets(10, 0, 6, 0));

        Label lieu = classe(new Label("Dakar, le " + java.time.LocalDate.now().format(
                DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale.FRENCH))), "lettre-ref");
        javafx.scene.layout.Region r1 = new javafx.scene.layout.Region();
        javafx.scene.layout.HBox.setHgrow(r1, javafx.scene.layout.Priority.ALWAYS);
        Label ref = classe(new Label(verification == null ? "" : "Code de vérification : " + verification.codeLisible()),
                "lettre-ref");
        javafx.scene.layout.HBox ligneRef = new javafx.scene.layout.HBox(ref, r1, lieu);

        VBox corps = new VBox(14, enteteUcad(), ligneRef, cadreTitre, tableau(lignes));
        if (basDePage != null) {
            corps.getChildren().add(enveloppe(classe(new Label(basDePage), "lettre-petit"), LETTRE_UTILE));
        }
        Label pour = classe(new Label("Pour le Recteur, et par délégation"), "lettre-petit");
        Label signataire = classe(new Label("Le Chef du service de la scolarité"), "lettre-signature");
        VBox blocSignature = new VBox(6, pour, signataire, cachet());
        blocSignature.setAlignment(javafx.geometry.Pos.TOP_CENTER);
        corps.getChildren().add(ligneSignature(verification, blocSignature));

        javafx.scene.layout.Region ressort = new javafx.scene.layout.Region();
        VBox.setVgrow(ressort, javafx.scene.layout.Priority.ALWAYS);
        VBox page = new VBox(corps, ressort, pied());
        page.getStyleClass().add("lettre");
        page.setPrefWidth(LETTRE_LARGEUR);
        page.setMinSize(LETTRE_LARGEUR, LETTRE_HAUTEUR);
        page.setMaxWidth(LETTRE_LARGEUR);
        return page;
    }

    /** Aperçu d'une page officielle, puis impression ou enregistrement en PDF. */
    public static void apercuEtImpression(String titre, List<String[]> lignes, String basDePage) {
        apercuEtImpression(titre, lignes, basDePage, null);
    }

    public static void apercuEtImpression(String titre, List<String[]> lignes, String basDePage,
            com.tdsi.memotheque.model.DocumentEmis verification) {
        apercuEtImpressionLettres(titre, List.of(document(titre, lignes, basDePage, verification)));
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
        GridPane grille = tableau(c.details());

        Label fin = enveloppe(classe(new Label(c.formuleFin()), "lettre-paragraphe"), LETTRE_UTILE);

        // ---- signature et cachet
        Label pour = classe(new Label("Pour le Recteur, et par délégation"), "lettre-petit");
        Label signataire = classe(new Label(c.signature()), "lettre-signature");
        VBox blocSignature = new VBox(6, pour, signataire, cachet());
        blocSignature.setAlignment(javafx.geometry.Pos.TOP_CENTER);
        javafx.scene.layout.HBox ligneSignature = ligneSignature(c.document(), blocSignature);

        // ---- pied de page, poussé en bas de la feuille
        javafx.scene.layout.Region ressort = new javafx.scene.layout.Region();
        VBox.setVgrow(ressort, javafx.scene.layout.Priority.ALWAYS);
        VBox pied = pied();

        VBox page = new VBox(enteteUcad(), ligneRef, cadreTitre, ligneDest, ligneObjet, corps, grille, fin,
                ligneSignature, ressort, pied);
        page.getStyleClass().add("lettre");
        // Au moins une feuille A4 ; le pied de page est poussé en bas par le ressort.
        page.setPrefWidth(LETTRE_LARGEUR);
        page.setMinSize(LETTRE_LARGEUR, LETTRE_HAUTEUR);
        page.setMaxWidth(LETTRE_LARGEUR);
        return page;
    }

    /** En-tête officiel : République du Sénégal, sceau de l'UCAD, faculté, département, filière, scolarité. */
    static VBox enteteUcad() {
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

        return new VBox(8, republique, entete, filets);
    }

    /** Tableau encadré libellé / valeur. */
    static GridPane tableau(List<String[]> lignes) {
        GridPane grille = new GridPane();
        grille.getStyleClass().add("lettre-tableau");
        javafx.scene.layout.ColumnConstraints col1 = new javafx.scene.layout.ColumnConstraints();
        col1.setMinWidth(165);
        col1.setPrefWidth(165);
        javafx.scene.layout.ColumnConstraints col2 = new javafx.scene.layout.ColumnConstraints();
        col2.setHgrow(javafx.scene.layout.Priority.ALWAYS);
        grille.getColumnConstraints().addAll(col1, col2);
        for (int i = 0; i < lignes.size(); i++) {
            String valeur = lignes.get(i)[1];
            Label k = classe(new Label(lignes.get(i)[0]), "lettre-cle");
            Label v = classe(new Label(valeur == null || valeur.isBlank() ? "-" : valeur), "lettre-valeur");
            k.setMaxWidth(Double.MAX_VALUE);
            k.setMaxHeight(Double.MAX_VALUE);
            k.setWrapText(true);
            v.setMaxWidth(Double.MAX_VALUE);
            enveloppe(v, LETTRE_UTILE - 168);
            if (i % 2 == 1) {
                v.getStyleClass().add("lettre-valeur-alt");
            }
            grille.add(k, 0, i);
            grille.add(v, 1, i);
        }
        return grille;
    }

    private static javafx.scene.layout.StackPane cachet() {
        javafx.scene.layout.StackPane cachet = new javafx.scene.layout.StackPane(
                classe(new Label("Cachet\net signature"), "lettre-cachet-texte"));
        cachet.getStyleClass().add("lettre-cachet");
        cachet.setPrefSize(118, 82);
        cachet.setMaxSize(118, 82);
        return cachet;
    }

    /** QR code de vérification à gauche (s'il existe), signature à droite. */
    private static javafx.scene.layout.HBox ligneSignature(com.tdsi.memotheque.model.DocumentEmis d, VBox blocSignature) {
        javafx.scene.layout.Region r3 = new javafx.scene.layout.Region();
        javafx.scene.layout.HBox.setHgrow(r3, javafx.scene.layout.Priority.ALWAYS);
        blocSignature.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        javafx.scene.layout.HBox ligne = d == null ? new javafx.scene.layout.HBox(r3, blocSignature)
                : new javafx.scene.layout.HBox(blocVerification(d), r3, blocSignature);
        ligne.setPadding(new Insets(4, 10, 0, 0));
        return ligne;
    }

    /** QR code signé (Ed25519 de l'établissement) et code à saisir dans MémoTDSI. */
    static javafx.scene.layout.HBox blocVerification(com.tdsi.memotheque.model.DocumentEmis d) {
        javafx.scene.image.ImageView qr = QrCode.encoder(
                com.tdsi.memotheque.service.VerificationService.texteQr(d)).vue(140);
        Label titre = classe(new Label("Document vérifiable"), "lettre-verif-titre");
        Label aide = enveloppe(classe(new Label("Scannez le QR code ou saisissez ce code dans MémoTDSI, "
                + "rubrique « Vérifier un document »."), "lettre-petit"), 148);
        Label code = classe(new Label(d.codeLisible()), "lettre-verif-code");
        code.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        VBox texte = new VBox(4, titre, aide, code);
        javafx.scene.layout.HBox h = new javafx.scene.layout.HBox(10, qr, texte);
        h.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return h;
    }

    private static VBox pied() {
        javafx.scene.layout.Region filetPied = classe(new javafx.scene.layout.Region(), "lettre-filet-fin");
        Label adresse = classe(new Label("Université Cheikh Anta Diop de Dakar — BP 5005 Dakar-Fann, Sénégal — www.ucad.sn"),
                "lettre-pied");
        Label edition = classe(new Label("Document édité par MémoTDSI le " + LocalDateTime.now().format(HORODATAGE)
                + " — référence à rappeler dans toute correspondance."), "lettre-pied-petit");
        VBox pied = new VBox(2, filetPied, adresse, edition);
        pied.setAlignment(javafx.geometry.Pos.CENTER);
        return pied;
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

    /** Choix du fichier puis export PDF (une page A4 par document). */
    static void enregistrerPdf(String titre, List<VBox> pages, javafx.stage.Window fenetre) {
        javafx.stage.FileChooser choix = new javafx.stage.FileChooser();
        choix.setTitle("Enregistrer en PDF");
        choix.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Document PDF", "*.pdf"));
        choix.setInitialFileName(nomFichier(titre) + ".pdf");
        java.io.File f = choix.showSaveDialog(fenetre);
        if (f == null) {
            return;
        }
        java.nio.file.Path chemin = f.toPath();
        if (!chemin.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")) {
            chemin = chemin.resolveSibling(chemin.getFileName() + ".pdf");
        }
        try {
            PdfExport.ecrire(chemin, titre, pages);
            AlertUtil.info("Enregistrer en PDF", pages.size() + (pages.size() > 1 ? " pages enregistrées" : " page enregistrée")
                    + " dans :\n" + chemin);
        } catch (java.io.IOException | RuntimeException e) {
            AlertUtil.erreur("Enregistrer en PDF", "Impossible d'écrire le fichier : " + e.getMessage());
        }
    }

    /** « Convocations à la soutenance » -> « convocations-a-la-soutenance-2026-11-05 ». */
    static String nomFichier(String titre) {
        String s = java.text.Normalizer.normalize(titre, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return s + "-" + java.time.LocalDate.now();
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

        ButtonType imprimer = new ButtonType(pages.size() > 1 ? "Imprimer les " + pages.size() + " pages..." : "Imprimer...",
                ButtonBar.ButtonData.OK_DONE);
        ButtonType pdf = new ButtonType("Enregistrer en PDF...", ButtonBar.ButtonData.LEFT);
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle(titre);
        d.setHeaderText(titre + (pages.size() > 1 ? " — " + pages.size() + " pages" : ""));
        d.getDialogPane().setContent(defile);
        d.getDialogPane().getButtonTypes().addAll(pdf, imprimer, FERMER);
        // Le PDF s'enregistre sans fermer l'aperçu (on peut ensuite imprimer).
        d.getDialogPane().lookupButton(pdf).addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            ev.consume();
            enregistrerPdf(titre, pages, d.getDialogPane().getScene().getWindow());
        });
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

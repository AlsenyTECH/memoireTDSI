package com.tdsi.memotheque.controller;

import java.io.File;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;

import com.tdsi.memotheque.model.Domaine;
import com.tdsi.memotheque.model.Memoire;
import com.tdsi.memotheque.model.NiveauDiffusion;
import com.tdsi.memotheque.model.StatutMemoire;
import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.service.FichierService;
import com.tdsi.memotheque.service.MemoireService;
import com.tdsi.memotheque.service.ParametreService;
import com.tdsi.memotheque.util.AlertUtil;
import com.tdsi.memotheque.util.SceneNavigator;
import com.tdsi.memotheque.util.SessionManager;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;

/**
 * [M3] Formulaire de dépôt d'un mémoire (métadonnées + FileChooser PDF + niveau de diffusion).
 * L'année vient de la campagne de la scolarité et le niveau de l'inscription : affichés, non saisis.
 * Date de soutenance, jury, note et mention relèvent de la scolarité et du jury.
 * Sert aussi à la modification : « Mes mémoires » appelle preparerModification(m) avant de charger l'écran.
 */
public class DepotController {

    /** Mémoire à modifier, transmis par MesMemoiresController (null = nouveau dépôt). */
    private static Memoire memoireAModifier;

    @FXML private Label labelTitreEcran;
    @FXML private Label labelInfoModification;
    @FXML private TextField champTitre;
    @FXML private TextArea zoneResume;
    @FXML private TextField champMotsCles;
    @FXML private Label labelCampagne;
    @FXML private Label labelAnnee;
    @FXML private Label labelNiveau;
    @FXML private ComboBox<Domaine> comboDomaine;
    @FXML private ComboBox<Utilisateur> comboEncadreur;
    @FXML private TextField champCoAuteur;
    @FXML private ComboBox<NiveauDiffusion> comboDiffusion;
    @FXML private Label labelDiffusion;
    @FXML private Label labelEmbargo;
    @FXML private DatePicker dateFinEmbargo;
    @FXML private CheckBox caseConsentement;
    @FXML private Label labelFichier;
    @FXML private Label labelMessage;
    @FXML private Button boutonEnvoyer;

    private final MemoireService memoireService = new MemoireService();
    private final ParametreService parametreService = new ParametreService();

    private Path fichierChoisi;
    private Memoire enModification;

    /** Ouvre l'écran de dépôt en mode modification pour ce mémoire. */
    public static void preparerModification(Memoire memoire) {
        memoireAModifier = memoire;
    }

    @FXML
    private void initialize() {
        enModification = memoireAModifier;
        memoireAModifier = null;

        comboDiffusion.setItems(FXCollections.observableArrayList(NiveauDiffusion.values()));
        comboDiffusion.valueProperty().addListener((obs, avant, apres) -> majDiffusion(apres));
        comboDiffusion.setValue(NiveauDiffusion.PUBLIC);

        try {
            comboDomaine.setItems(FXCollections.observableArrayList(memoireService.listerDomaines()));
            comboEncadreur.setItems(FXCollections.observableArrayList(memoireService.listerEncadreurs()));
        } catch (SQLException e) {
            afficherErreur("Impossible de charger les listes : " + e.getMessage());
        }

        if (enModification == null) {
            preremplirNouveauDepot();
        } else {
            preremplirModification(enModification);
        }
    }

    private void preremplirNouveauDepot() {
        Utilisateur u = SessionManager.getUtilisateur();
        labelNiveau.setText(u == null || u.getNiveau() == null
                ? "Non renseigné : contactez la scolarité" : u.getNiveau() + " (votre inscription)");
        try {
            ParametreService.Campagne c = parametreService.campagne();
            labelAnnee.setText(c.anneeCourante() + " (fixée par la scolarité)");
            if (c.depotPossible(LocalDate.now())) {
                labelCampagne.setText("Campagne de dépôt " + c.anneeCourante() + " ouverte"
                        + (c.dateLimite() == null ? "." : " jusqu'au " + c.dateLimite() + "."));
            } else {
                labelCampagne.setText(c.ouverte()
                        ? "La date limite de dépôt (" + c.dateLimite() + ") est dépassée. Contactez la scolarité."
                        : "La campagne de dépôt est fermée par la scolarité.");
                labelCampagne.getStyleClass().setAll("bandeau-danger");
                boutonEnvoyer.setDisable(true);
            }
        } catch (SQLException e) {
            afficherErreur("Impossible de lire la campagne de dépôt : " + e.getMessage());
        }
    }

    /** L'embargo n'a de sens que pour un mémoire confidentiel. */
    private void majDiffusion(NiveauDiffusion d) {
        boolean confidentiel = d == NiveauDiffusion.CONFIDENTIEL;
        labelDiffusion.setText(d == null ? "" : d.getDescription());
        labelEmbargo.setVisible(confidentiel);
        labelEmbargo.setManaged(confidentiel);
        dateFinEmbargo.setVisible(confidentiel);
        dateFinEmbargo.setManaged(confidentiel);
        if (!confidentiel) {
            dateFinEmbargo.setValue(null);
        }
    }

    private void preremplirModification(Memoire m) {
        labelTitreEcran.setText("Modifier mon mémoire");
        boutonEnvoyer.setText("Enregistrer les modifications");

        if (m.getStatut() == StatutMemoire.REFUSE && m.getMotifRefus() != null) {
            labelInfoModification.setText("Corrections demandées : " + m.getMotifRefus()
                    + "\nAprès correction, le mémoire sera de nouveau soumis à votre encadreur.");
        } else {
            labelInfoModification.setText("Le mémoire reste soumis à votre encadreur.");
        }

        champTitre.setText(m.getTitre());
        zoneResume.setText(m.getResume());
        champMotsCles.setText(m.getMotsCles());
        labelAnnee.setText(m.getAnneeAcademique());
        labelNiveau.setText(String.valueOf(m.getNiveau()));
        champCoAuteur.setText(m.getCoAuteur());
        comboDiffusion.setValue(m.getNiveauDiffusion() == null ? NiveauDiffusion.PUBLIC : m.getNiveauDiffusion());
        dateFinEmbargo.setValue(m.getDateFinEmbargo());
        caseConsentement.setSelected(true);

        if (m.getDomaine() != null) {
            comboDomaine.getItems().stream()
                    .filter(d -> d.getId() == m.getDomaine().getId())
                    .findFirst().ifPresent(comboDomaine::setValue);
        }
        if (m.getEncadreur() != null) {
            comboEncadreur.getItems().stream()
                    .filter(e -> e.getId() == m.getEncadreur().getId())
                    .findFirst().ifPresent(comboEncadreur::setValue);
        }

        labelFichier.setText("Fichier actuel : " + m.getNomOriginal()
                + " (choisir un PDF seulement pour le remplacer)");
    }

    @FXML
    private void choisirFichier() {
        FileChooser choix = new FileChooser();
        choix.setTitle("Choisir le mémoire (PDF, 20 Mo maximum)");
        choix.getExtensionFilters().add(new FileChooser.ExtensionFilter("Fichier PDF", "*.pdf"));

        File fichier = choix.showOpenDialog(labelMessage.getScene().getWindow());
        if (fichier == null) {
            return;
        }

        if (fichier.length() > FichierService.TAILLE_MAX_OCTETS) {
            afficherErreur("Le fichier dépasse la taille maximale de 20 Mo.");
            return;
        }

        fichierChoisi = fichier.toPath();
        labelFichier.setText(fichier.getName() + " (" + (fichier.length() / 1024) + " Ko)");
        afficherErreur("");
    }

    @FXML
    private void envoyer() {
        Memoire m = lireFormulaire();
        boolean nouveau = enModification == null;

        if (nouveau && fichierChoisi == null) {
            afficherErreur("Choisissez le fichier PDF du mémoire.");
            return;
        }
        if (!caseConsentement.isSelected()) {
            afficherErreur("Cochez la déclaration d'auteur et d'autorisation de diffusion.");
            return;
        }
        if (!nouveau) {
            m.setId(enModification.getId());
        }
        Path pdf = fichierChoisi;
        afficherErreur("Chiffrement et enregistrement en cours...");

        // Lecture, contrôle, SHA-256 et chiffrement du PDF : en tâche de fond.
        com.tdsi.memotheque.util.TacheFond.executer(() -> {
            if (nouveau) {
                memoireService.deposer(m, pdf, true);
            } else {
                memoireService.modifier(m, pdf);
            }
            return null;
        }, ok -> {
            afficherErreur("");
            AlertUtil.info(nouveau ? "Dépôt" : "Modification", nouveau
                    ? "Votre mémoire a été déposé (chiffré) et soumis à votre encadreur.\n"
                            + "Vous serez notifié de son avis puis de la date de soutenance."
                    : "Votre mémoire a été modifié et de nouveau soumis à votre encadreur.");
            SceneNavigator.charger("mes_memoires");
        }, e -> afficherErreur(com.tdsi.memotheque.util.TacheFond.message(e)), boutonEnvoyer);
    }

    @FXML
    private void annuler() {
        SceneNavigator.charger(enModification == null ? "dashboard" : "mes_memoires");
    }

    private Memoire lireFormulaire() {
        Memoire m = new Memoire();
        m.setTitre(champTitre.getText());
        m.setResume(zoneResume.getText());
        m.setMotsCles(champMotsCles.getText());
        m.setDomaine(comboDomaine.getValue());
        m.setEncadreur(comboEncadreur.getValue());
        m.setCoAuteur(champCoAuteur.getText());
        m.setNiveauDiffusion(comboDiffusion.getValue());
        m.setDateFinEmbargo(dateFinEmbargo.getValue());
        return m;
    }

    private void afficherErreur(String message) {
        labelMessage.setText(message);
    }
}

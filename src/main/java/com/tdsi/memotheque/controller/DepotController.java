package com.tdsi.memotheque.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;

import com.tdsi.memotheque.model.Domaine;
import com.tdsi.memotheque.model.Memoire;
import com.tdsi.memotheque.model.Mention;
import com.tdsi.memotheque.model.Niveau;
import com.tdsi.memotheque.model.StatutMemoire;
import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.service.FichierService;
import com.tdsi.memotheque.service.MemoireService;
import com.tdsi.memotheque.util.AlertUtil;
import com.tdsi.memotheque.util.SceneNavigator;
import com.tdsi.memotheque.util.SessionManager;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;

/**
 * [M3] Formulaire de dépôt d'un mémoire (métadonnées + FileChooser PDF + DatePicker).
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
    @FXML private TextField champAnnee;
    @FXML private ComboBox<Niveau> comboNiveau;
    @FXML private ComboBox<Domaine> comboDomaine;
    @FXML private ComboBox<Utilisateur> comboEncadreur;
    @FXML private TextField champCoAuteur;
    @FXML private DatePicker dateSoutenance;
    @FXML private ComboBox<Mention> comboMention;
    @FXML private Label labelFichier;
    @FXML private Label labelMessage;
    @FXML private Button boutonEnvoyer;

    private final MemoireService memoireService = new MemoireService();

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

        comboNiveau.setItems(FXCollections.observableArrayList(Niveau.values()));
        comboMention.setItems(FXCollections.observableArrayList(Mention.values()));

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
        LocalDate aujourdhui = LocalDate.now();
        // L'année académique commence en octobre.
        int debut = aujourdhui.getMonthValue() >= 10 ? aujourdhui.getYear() : aujourdhui.getYear() - 1;
        champAnnee.setText(debut + "-" + (debut + 1));

        Utilisateur u = SessionManager.getUtilisateur();
        if (u != null) {
            comboNiveau.setValue(u.getNiveau());
        }
    }

    private void preremplirModification(Memoire m) {
        labelTitreEcran.setText("Modifier mon mémoire");
        boutonEnvoyer.setText("Enregistrer les modifications");

        if (m.getStatut() == StatutMemoire.REFUSE && m.getMotifRefus() != null) {
            labelInfoModification.setText("Motif du refus : " + m.getMotifRefus()
                    + "\nAprès correction, le mémoire repassera « En attente ».");
        } else {
            labelInfoModification.setText("Le mémoire restera « En attente » de validation.");
        }

        champTitre.setText(m.getTitre());
        zoneResume.setText(m.getResume());
        champMotsCles.setText(m.getMotsCles());
        champAnnee.setText(m.getAnneeAcademique());
        comboNiveau.setValue(m.getNiveau());
        champCoAuteur.setText(m.getCoAuteur());
        dateSoutenance.setValue(m.getDateSoutenance());
        comboMention.setValue(m.getMention());

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

        try {
            if (enModification == null) {
                if (fichierChoisi == null) {
                    afficherErreur("Choisissez le fichier PDF du mémoire.");
                    return;
                }
                memoireService.deposer(m, fichierChoisi);
                AlertUtil.info("Dépôt", "Votre mémoire a été déposé. Il est en attente de validation.");
            } else {
                m.setId(enModification.getId());
                memoireService.modifier(m, fichierChoisi);
                AlertUtil.info("Modification", "Votre mémoire a été modifié et repasse en attente.");
            }
            SceneNavigator.charger("mes_memoires");

        } catch (IllegalArgumentException | IllegalStateException | SecurityException e) {
            afficherErreur(e.getMessage());
        } catch (SQLException | IOException e) {
            afficherErreur("Erreur lors de l'enregistrement : " + e.getMessage());
        }
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
        m.setAnneeAcademique(champAnnee.getText());
        m.setNiveau(comboNiveau.getValue());
        m.setDomaine(comboDomaine.getValue());
        m.setEncadreur(comboEncadreur.getValue());
        m.setCoAuteur(champCoAuteur.getText());
        m.setDateSoutenance(dateSoutenance.getValue());
        m.setMention(comboMention.getValue());
        return m;
    }

    private void afficherErreur(String message) {
        labelMessage.setText(message);
    }
}

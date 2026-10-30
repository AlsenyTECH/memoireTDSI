package com.tdsi.memotheque.controller;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.tdsi.memotheque.model.Memoire;
import com.tdsi.memotheque.model.Soutenance;
import com.tdsi.memotheque.model.StatutMemoire;
import com.tdsi.memotheque.service.MemoireService;
import com.tdsi.memotheque.service.SoutenanceService;
import com.tdsi.memotheque.util.AlertUtil;
import com.tdsi.memotheque.util.Impression;
import com.tdsi.memotheque.util.Libelles;
import com.tdsi.memotheque.util.SceneNavigator;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

/**
 * [M3] « Mes mémoires » : suivi du circuit (avis de l'encadreur, soutenance, résultat),
 * modification / suppression tant que l'encadreur n'a pas autorisé la soutenance,
 * attestation de dépôt imprimable.
 */
public class MesMemoiresController {

    @FXML private TableView<Memoire> tableMemoires;
    @FXML private TableColumn<Memoire, String> colTitre;
    @FXML private TableColumn<Memoire, String> colNiveau;
    @FXML private TableColumn<Memoire, StatutMemoire> colStatut;
    @FXML private TableColumn<Memoire, String> colSoutenance;
    @FXML private TableColumn<Memoire, String> colResultat;
    @FXML private TableColumn<Memoire, String> colSuivi;
    @FXML private Button boutonModifier;
    @FXML private Button boutonSupprimer;
    @FXML private Button boutonDetail;
    @FXML private Button boutonHistorique;
    @FXML private Button boutonAttestation;
    @FXML private Button boutonConvocation;
    @FXML private Label labelMessage;

    private final MemoireService memoireService = new MemoireService();
    private final SoutenanceService soutenanceService = new SoutenanceService();
    private final ObservableList<Memoire> memoires = FXCollections.observableArrayList();
    private final Map<Integer, Soutenance> soutenances = new HashMap<>();

    @FXML
    private void initialize() {
        colTitre.setCellValueFactory(new PropertyValueFactory<>("titre"));
        colNiveau.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getNiveau() + " " + c.getValue().getAnneeAcademique()));
        colStatut.setCellValueFactory(new PropertyValueFactory<>("statut"));
        colSoutenance.setCellValueFactory(c -> new SimpleStringProperty(soutenanceLisible(c.getValue())));
        colResultat.setCellValueFactory(c -> new SimpleStringProperty(resultat(c.getValue())));
        colSuivi.setCellValueFactory(c -> new SimpleStringProperty(suivi(c.getValue())));

        colStatut.setCellFactory(colonne -> Libelles.cellulePastille());

        tableMemoires.setItems(memoires);
        tableMemoires.getSelectionModel().selectedItemProperty()
                .addListener((obs, ancien, nouveau) -> majBoutons(nouveau));
        majBoutons(null);

        charger();
    }

    private void charger() {
        try {
            soutenances.clear();
            for (Soutenance s : soutenanceService.soutenances()) {
                soutenances.put(s.getIdMemoire(), s);
            }
            memoires.setAll(memoireService.mesMemoires());
            labelMessage.setText(memoires.isEmpty() ? "Vous n'avez encore déposé aucun mémoire." : "");
        } catch (SQLException | SecurityException e) {
            labelMessage.setText("Impossible de charger vos mémoires : " + e.getMessage());
        }
    }

    private String soutenanceLisible(Memoire m) {
        return Libelles.soutenance(m, soutenances.get(m.getId()));
    }

    private static String resultat(Memoire m) {
        return Libelles.resultat(m);
    }

    /** Ce qui se passe ensuite, en une phrase, comme sur un portail de scolarité. */
    private String suivi(Memoire m) {
        return switch (m.getStatut()) {
            case EN_ATTENTE -> "En attente de l'avis de " + m.getEncadreurNomComplet() + ".";
            case REFUSE -> "À corriger : " + (m.getMotifRefus() == null ? "voir le détail." : m.getMotifRefus());
            case AUTORISE -> soutenances.containsKey(m.getId())
                    ? "Soutenance programmée : préparez votre présentation."
                    : "Autorisé à soutenir : la scolarité fixe la date et le jury.";
            case VALIDE -> m.estSousEmbargo(java.time.LocalDate.now())
                    ? "Publié, sous embargo jusqu'au " + Libelles.date(m.getDateFinEmbargo()) + "."
                    : "Publié le " + Libelles.date(m.getDatePublication()) + ".";
        };
    }

    private void majBoutons(Memoire m) {
        boolean modifiable = m != null
                && (m.getStatut() == StatutMemoire.EN_ATTENTE || m.getStatut() == StatutMemoire.REFUSE);
        boutonModifier.setDisable(!modifiable);
        boutonSupprimer.setDisable(!modifiable);
        boutonDetail.setDisable(m == null);
        boutonHistorique.setDisable(m == null);
        boutonAttestation.setDisable(m == null);
        Soutenance s = m == null ? null : soutenances.get(m.getId());
        boutonConvocation.setDisable(s == null || s.getStatut() != Soutenance.Statut.PLANIFIEE);
    }

    @FXML
    private void modifier() {
        Memoire m = tableMemoires.getSelectionModel().getSelectedItem();
        if (m == null) {
            return;
        }
        DepotController.preparerModification(m);
        SceneNavigator.charger("depot");
    }

    @FXML
    private void supprimer() {
        Memoire m = tableMemoires.getSelectionModel().getSelectedItem();
        if (m == null) {
            return;
        }
        if (!AlertUtil.confirmer("Suppression",
                "Supprimer définitivement le mémoire « " + m.getTitre() + " » et son fichier ?")) {
            return;
        }
        try {
            memoireService.supprimer(m.getId());
            charger();
            labelMessage.setText("Mémoire supprimé.");
        } catch (IllegalArgumentException | IllegalStateException | SecurityException e) {
            labelMessage.setText(e.getMessage());
        } catch (SQLException | IOException e) {
            labelMessage.setText("Erreur lors de la suppression : " + e.getMessage());
        }
    }

    @FXML
    private void voirDetail() {
        Memoire m = tableMemoires.getSelectionModel().getSelectedItem();
        if (m != null) {
            SceneNavigator.afficherDetailMemoire(m);
        }
    }

    @FXML
    private void voirHistorique() {
        Memoire m = tableMemoires.getSelectionModel().getSelectedItem();
        if (m == null) {
            return;
        }
        try {
            java.util.List<com.tdsi.memotheque.model.MemoireVersion> versions = memoireService.historique(m.getId());
            if (versions.isEmpty()) {
                AlertUtil.info("Historique", "Aucune version précédente : le PDF n'a jamais été remplacé.");
                return;
            }
            AlertUtil.afficherTableau("Historique des versions — " + m.getTitre(),
                    new String[] {"Remplacé le", "Fichier", "Taille", "Statut alors", "Motif du refus", "SHA-256"},
                    versions.stream().map(v -> new String[] {
                            v.getDateArchivage().toLocalDate().toString(), v.getNomOriginal(), v.getTailleLisible(),
                            v.getStatut().getLibelle(), v.getMotifRefus() == null ? "-" : v.getMotifRefus(),
                            v.getEmpreinteSha256().substring(0, 16) + "…"}).toList());
        } catch (SQLException | SecurityException | IllegalArgumentException e) {
            labelMessage.setText("Historique indisponible : " + e.getMessage());
        }
    }

    /** Attestation de dépôt : preuve horodatée avec l'empreinte SHA-256 du PDF déposé. */
    @FXML
    private void imprimerAttestation() {
        Memoire m = tableMemoires.getSelectionModel().getSelectedItem();
        if (m == null) {
            return;
        }
        Impression.apercuEtImpression("Attestation de dépôt de mémoire", List.of(
                new String[] {"Étudiant", m.getAuteurNomComplet()},
                new String[] {"Titre", m.getTitre()},
                new String[] {"Niveau / année", m.getNiveau() + " — " + m.getAnneeAcademique()},
                new String[] {"Domaine", m.getDomaineNom()},
                new String[] {"Encadreur", m.getEncadreurNomComplet()},
                new String[] {"Déposé le", Libelles.date(m.getDateDepot())},
                new String[] {"N° de dépôt", "#" + m.getId()},
                new String[] {"Fichier", m.getNomOriginal()},
                new String[] {"Empreinte SHA-256", m.getEmpreinteSha256()},
                new String[] {"Diffusion", m.getNiveauDiffusion() == null ? "-" : m.getNiveauDiffusion().getLibelle()},
                new String[] {"État actuel", m.getStatut().getLibelle()},
                new String[] {"Soutenance", soutenanceLisible(m)},
                new String[] {"Résultat", resultat(m)}),
                "L'empreinte SHA-256 identifie de façon unique le fichier déposé : toute modification du PDF la change.");
    }

    /** Convocation à la soutenance programmée (lettre imprimable). */
    @FXML
    private void voirConvocation() {
        Memoire m = tableMemoires.getSelectionModel().getSelectedItem();
        if (m != null) {
            SoutenancesController.afficherConvocations(m.getId(), labelMessage);
        }
    }

    @FXML
    private void nouveauDepot() {
        SceneNavigator.charger("depot");
    }
}

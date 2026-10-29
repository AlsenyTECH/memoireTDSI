
package com.tdsi.memotheque.controller;

import java.time.LocalDate;
import javafx.scene.control.TableCell;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.control.Label;

import java.sql.SQLException;

import java.util.List;

import com.tdsi.memotheque.dao.impl.MemoireRechercheDAOImpl;
import com.tdsi.memotheque.model.Domaine;
import com.tdsi.memotheque.model.Memoire;
import com.tdsi.memotheque.model.MemoireCritere;
import com.tdsi.memotheque.model.Mention;
import com.tdsi.memotheque.model.Niveau;
import com.tdsi.memotheque.model.TriMemoire;
import com.tdsi.memotheque.util.DatabaseConnection;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;

import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

import com.tdsi.memotheque.service.RechercheService;
import com.tdsi.memotheque.util.SceneNavigator;


import javafx.scene.input.MouseButton;

public class RechercheController {

    @FXML private javafx.scene.control.TextField champMotCle;
    @FXML private ComboBox<Domaine> comboDomaine;
    @FXML private ComboBox<Niveau> comboNiveau;
    @FXML private ComboBox<String> comboAnnee;
    @FXML private ComboBox<Mention> comboMention;
    @FXML private DatePicker dateDebut;
    @FXML private DatePicker dateFin;
    @FXML private ComboBox<TriMemoire> comboTri;

    @FXML private TableView<Memoire> tableMemoires;
    @FXML private TableColumn<Memoire, String> colTitre;
    @FXML private TableColumn<Memoire, String> colDomaine;
    @FXML private TableColumn<Memoire, String> colAuteur;
    @FXML private TableColumn<Memoire, String> colNiveau;
    @FXML private TableColumn<Memoire, String> colAnnee;
    @FXML private TableColumn<Memoire, String> colMention;

    @FXML private Label labelCompteur;
    @FXML private Label labelMessage;
    @FXML private Label labelPagination;
    @FXML private Button boutonPrecedent;
    @FXML private Button boutonSuivant;

    private final RechercheService rechercheService =
            new RechercheService();

    private final MemoireCritere critere = new MemoireCritere();
    private final ObservableList<Memoire> resultats =
            FXCollections.observableArrayList();

    private int totalResultats = 0;
    private static final int TAILLE_PAGE = 20;

    @FXML
    public void initialize() {
        configurerTableau();
        configurerFiltres();
        configurerBadgeNouveau();
        configurerInfobulles();

        try {
            chargerDomaines();
            chargerAnnees();
            rechercher();
        } catch (SQLException e) {
            afficherErreur("Impossible de charger les données : "
                    + e.getMessage());
        }
    }

    private void configurerTableau() {
        colTitre.setCellValueFactory(
                new PropertyValueFactory<>("titre"));
        colDomaine.setCellValueFactory(
                new PropertyValueFactory<>("domaineNom"));
        colAuteur.setCellValueFactory(
                new PropertyValueFactory<>("auteurNomComplet"));
        colNiveau.setCellValueFactory(
                new PropertyValueFactory<>("niveau"));
        colAnnee.setCellValueFactory(
                new PropertyValueFactory<>("anneeAcademique"));
        colMention.setCellValueFactory(
                new PropertyValueFactory<>("mention"));

        tableMemoires.setItems(resultats);
        
        tableMemoires.setRowFactory(tv -> {
            javafx.scene.control.TableRow<Memoire> ligne =
                    new javafx.scene.control.TableRow<>();

            ligne.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY
                        && event.getClickCount() == 2
                        && !ligne.isEmpty()) {

                    Memoire memoireSelectionne = ligne.getItem();

                    try {
                    	SceneNavigator.afficherDetailMemoire(memoireSelectionne);
                    } catch (Exception e) {
                        afficherErreur(
                                "Impossible d'ouvrir le détail : "
                                + e.getMessage());
                    }
                }
            });

            return ligne;
        });
    }

    private void configurerFiltres() {
        comboNiveau.setItems(
                FXCollections.observableArrayList(Niveau.values()));

        comboMention.setItems(
                FXCollections.observableArrayList(Mention.values()));

        comboTri.setItems(
                FXCollections.observableArrayList(TriMemoire.values()));

        comboTri.setValue(TriMemoire.PLUS_RECENTS);
    }

    private void chargerDomaines() throws SQLException {
        // La liste des domaines est chargée depuis la base.
        List<Domaine> domaines = listerDomaines();

        comboDomaine.setItems(
                FXCollections.observableArrayList(domaines));

        comboDomaine.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Domaine domaine) {
                return domaine == null ? "" : domaine.getNom();
            }

            @Override
            public Domaine fromString(String texte) {
                return null;
            }
        });
    }

    private List<Domaine> listerDomaines() throws SQLException {
        List<Domaine> domaines = new java.util.ArrayList<>();

        String sql = "SELECT id, nom, description FROM domaine ORDER BY nom";

        try (var connexion = DatabaseConnection.getConnection();
             var ps = connexion.prepareStatement(sql);
             var rs = ps.executeQuery()) {

            while (rs.next()) {
                Domaine domaine = new Domaine();
                domaine.setId(rs.getInt("id"));
                domaine.setNom(rs.getString("nom"));
                domaine.setDescription(rs.getString("description"));
                domaines.add(domaine);
            }
        }

        return domaines;
    }

    private void chargerAnnees() throws SQLException {
        comboAnnee.setItems(
                FXCollections.observableArrayList(
                		rechercheService.listerAnneesAcademiques()));
    }

    @FXML
    public void rechercher() {
        try {
            lireFiltres();

            List<Memoire> page =
                    rechercheService.rechercherPublic(critere);

            totalResultats =
                    rechercheService.compterPublic(critere);
            
            resultats.setAll(page);
            labelCompteur.setText(
                    totalResultats + " résultat(s)");

            int nombrePages = Math.max(
                    1, (int) Math.ceil(
                            (double) totalResultats / TAILLE_PAGE));

            labelPagination.setText(
                    "Page " + critere.getPage()
                            + " / " + nombrePages);

            boutonPrecedent.setDisable(critere.getPage() <= 1);
            boutonSuivant.setDisable(
                    critere.getPage() >= nombrePages);

            labelMessage.setText("");

        } catch (SQLException | IllegalArgumentException e) {
            afficherErreur("Erreur de recherche : " + e.getMessage());
        }
    }

    private void lireFiltres() {
        critere.setMotCle(texteOuNull(champMotCle.getText()));

        Domaine domaine = comboDomaine.getValue();
        critere.setIdDomaine(
                domaine == null ? null : domaine.getId());

        critere.setNiveau(comboNiveau.getValue());
        critere.setAnneeAcademique(comboAnnee.getValue());
        critere.setMention(comboMention.getValue());
        critere.setDateDebut(dateDebut.getValue());
        critere.setDateFin(dateFin.getValue());
        critere.setTri(comboTri.getValue());

        critere.setPage(1);
        critere.setTaille(TAILLE_PAGE);
    }

    @FXML
    public void reinitialiser() {
        champMotCle.clear();
        comboDomaine.getSelectionModel().clearSelection();
        comboNiveau.getSelectionModel().clearSelection();
        comboAnnee.getSelectionModel().clearSelection();
        comboMention.getSelectionModel().clearSelection();
        dateDebut.setValue(null);
        dateFin.setValue(null);
        comboTri.setValue(TriMemoire.PLUS_RECENTS);

        critere.setPage(1);
        rechercher();
    }

    @FXML
    public void pagePrecedente() {
        if (critere.getPage() > 1) {
            critere.setPage(critere.getPage() - 1);
            rechercherSansReinitialiserPage();
        }
    }

    @FXML
    public void pageSuivante() {
        int nombrePages = Math.max(
                1, (int) Math.ceil(
                        (double) totalResultats / TAILLE_PAGE));

        if (critere.getPage() < nombrePages) {
            critere.setPage(critere.getPage() + 1);
            rechercherSansReinitialiserPage();
        }
    }

    private void rechercherSansReinitialiserPage() {
        try {
        	List<Memoire> page = rechercheService.rechercherPublic(critere);
            resultats.setAll(page);

            int nombrePages = Math.max(
                    1, (int) Math.ceil(
                            (double) totalResultats / TAILLE_PAGE));

            labelPagination.setText(
                    "Page " + critere.getPage()
                            + " / " + nombrePages);

            boutonPrecedent.setDisable(critere.getPage() <= 1);
            boutonSuivant.setDisable(
                    critere.getPage() >= nombrePages);

            labelMessage.setText("");

        } catch (SQLException e) {
            afficherErreur("Erreur de pagination : " + e.getMessage());
        }
    }

    private String texteOuNull(String texte) {
        if (texte == null || texte.isBlank()) {
            return null;
        }
        return texte.trim();
    }

    private void afficherErreur(String message) {
        labelMessage.setText(message);
    }
    
    private void configurerInfobulles() {
        champMotCle.setTooltip(
            new javafx.scene.control.Tooltip(
                "Rechercher dans le titre, le résumé et les mots-clés."
            )
        );

        comboDomaine.setTooltip(
            new javafx.scene.control.Tooltip("Filtrer par domaine.")
        );

        comboNiveau.setTooltip(
            new javafx.scene.control.Tooltip("Filtrer par niveau d'études.")
        );

        comboAnnee.setTooltip(
            new javafx.scene.control.Tooltip("Filtrer par année académique.")
        );

        comboMention.setTooltip(
            new javafx.scene.control.Tooltip("Filtrer par mention.")
        );

        dateDebut.setTooltip(
            new javafx.scene.control.Tooltip("Date minimale de publication.")
        );

        dateFin.setTooltip(
            new javafx.scene.control.Tooltip("Date maximale de publication.")
        );

        comboTri.setTooltip(
            new javafx.scene.control.Tooltip("Choisir l'ordre de tri des résultats.")
        );
    }
    
    private void configurerBadgeNouveau() {
        colTitre.setCellFactory(column -> new TableCell<Memoire, String>() {
            private final Label titre = new Label();
            private final Label badge = new Label("Nouveau");
            private final HBox contenu = new HBox(8, titre, badge);

            {
                badge.setStyle(
                    "-fx-background-color: #d1fae5;"
                    + "-fx-text-fill: #065f46;"
                    + "-fx-padding: 2 6 2 6;"
                    + "-fx-background-radius: 8;"
                    + "-fx-font-size: 10px;"
                    + "-fx-font-weight: bold;"
                );

                badge.setTooltip(new Tooltip("Mémoire publié récemment."));
                contenu.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            }

            @Override
            protected void updateItem(String valeur, boolean vide) {
                super.updateItem(valeur, vide);

                if (vide || valeur == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }

                titre.setText(valeur);
                Memoire memoire = getTableView().getItems().get(getIndex());

                boolean recent = memoire.getDatePublication() != null
                    && !memoire.getDatePublication().isBefore(
                        LocalDate.now().minusDays(30)
                    );

                badge.setVisible(recent);
                badge.setManaged(recent);

                setGraphic(contenu);
                setText(null);
            }
        });
    }
}

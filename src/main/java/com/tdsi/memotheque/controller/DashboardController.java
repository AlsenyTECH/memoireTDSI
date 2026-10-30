package com.tdsi.memotheque.controller;

import java.sql.SQLException;
import java.util.Map;

import com.tdsi.memotheque.model.Memoire;
import com.tdsi.memotheque.service.StatistiqueService;
import com.tdsi.memotheque.util.SceneNavigator;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseButton;

/** [M5] Tableau de bord : cartes de synthèse, PieChart par domaine, BarChart par année, top 5 des plus consultés. */
public class DashboardController {

    @FXML private Label labelTotalValides;
    @FXML private Label labelTotalEtudiants;
    @FXML private Label labelEnAttente;
    @FXML private PieChart graphiqueDomaines;
    @FXML private BarChart<String, Number> graphiqueAnnees;
    @FXML private javafx.scene.chart.NumberAxis axeNombre;
    @FXML private TableView<Memoire> tableTop;
    @FXML private TableColumn<Memoire, String> colTitre;
    @FXML private TableColumn<Memoire, String> colDomaine;
    @FXML private TableColumn<Memoire, Object> colVues;
    @FXML private TableColumn<Memoire, Object> colTelechargements;
    @FXML private Label labelMessage;
    @FXML private javafx.scene.layout.VBox blocAFaire;
    @FXML private javafx.scene.layout.StackPane bandeauAccueil;
    @FXML private Label labelBonjour;
    @FXML private Label labelSousBonjour;
    @FXML private javafx.scene.layout.StackPane iconePublies;
    @FXML private javafx.scene.layout.StackPane iconeEtudiants;
    @FXML private javafx.scene.layout.StackPane iconeAttente;

    private final StatistiqueService statistiqueService = new StatistiqueService();

    @FXML
    private void initialize() {
        colTitre.setCellValueFactory(new PropertyValueFactory<>("titre"));
        colDomaine.setCellValueFactory(new PropertyValueFactory<>("domaineNom"));
        colVues.setCellValueFactory(new PropertyValueFactory<>("nbVues"));
        colTelechargements.setCellValueFactory(new PropertyValueFactory<>("nbTelechargements"));

        tableTop.setRowFactory(tv -> {
            javafx.scene.control.TableRow<Memoire> ligne = new javafx.scene.control.TableRow<>();
            ligne.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2 && !ligne.isEmpty()) {
                    SceneNavigator.afficherDetailMemoire(ligne.getItem());
                }
            });
            return ligne;
        });

        accueillir();
        remplirAFaire();

        try {
            labelTotalValides.setText(String.valueOf(statistiqueService.totalMemoiresValides()));
            labelTotalEtudiants.setText(String.valueOf(statistiqueService.totalEtudiants()));
            labelEnAttente.setText(String.valueOf(statistiqueService.totalEnAttente()));

            remplirDomaines(statistiqueService.memoiresParDomaine());
            remplirAnnees(statistiqueService.memoiresParAnnee());
            tableTop.setItems(FXCollections.observableArrayList(statistiqueService.plusConsultes()));

            labelMessage.setText("");
        } catch (SQLException | SecurityException e) {
            labelMessage.setText("Statistiques indisponibles : " + e.getMessage());
        }
    }

    /** Bannière « Bonjour Awa » sur l'illustration (ou la photo) du campus. */
    private void accueillir() {
        iconePublies.getChildren().add(com.tdsi.memotheque.util.Icone.VERIFIE.creer(22));
        iconeEtudiants.getChildren().add(com.tdsi.memotheque.util.Icone.ECOLE.creer(22));
        iconeAttente.getChildren().add(com.tdsi.memotheque.util.Icone.ATTENTE.creer(22));
        String photo = com.tdsi.memotheque.util.Images.photoCampus();
        if (photo != null) {
            bandeauAccueil.setStyle("-fx-background-image: url(\"" + photo + "\"); -fx-background-position: center;");
        }
        com.tdsi.memotheque.model.Utilisateur u = com.tdsi.memotheque.util.SessionManager.getUtilisateur();
        if (u == null) {
            return;
        }
        int heure = java.time.LocalTime.now().getHour();
        labelBonjour.setText((heure >= 18 || heure < 5 ? "Bonsoir " : "Bonjour ")
                + (u.getPrenom() == null || u.getPrenom().isBlank() || u.getPrenom().endsWith(".")
                        ? u.getNomComplet() : u.getPrenom()));
        String date = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter
                .ofPattern("EEEE d MMMM yyyy", java.util.Locale.FRENCH));
        String role = switch (u.getRole()) {
            case ETUDIANT -> "Espace étudiant" + (u.getNiveau() == null ? "" : " · " + u.getNiveau());
            case ENCADREUR -> "Espace enseignant";
            case ADMIN -> "Scolarité";
        };
        labelSousBonjour.setText(Character.toUpperCase(date.charAt(0)) + date.substring(1) + " · " + role
                + " · Filière TDSI, Université Cheikh Anta Diop de Dakar");
    }

    /** Ce que l'utilisateur connecté doit faire maintenant, avec un lien vers l'écran concerné. */
    private void remplirAFaire() {
        com.tdsi.memotheque.model.Utilisateur u = com.tdsi.memotheque.util.SessionManager.getUtilisateur();
        if (u == null || u.getRole() == null) {
            return;
        }
        try {
            com.tdsi.memotheque.service.SoutenanceService soutenances = new com.tdsi.memotheque.service.SoutenanceService();
            java.time.LocalDateTime maintenant = java.time.LocalDateTime.now();
            switch (u.getRole()) {
                case ETUDIANT -> {
                    com.tdsi.memotheque.service.ParametreService.Campagne c =
                            new com.tdsi.memotheque.service.ParametreService().campagne();
                    ajouterAction(c.depotPossible(java.time.LocalDate.now())
                            ? "Campagne de dépôt " + c.anneeCourante() + " ouverte"
                                    + (c.dateLimite() == null ? "" : " jusqu'au " + c.dateLimite()) + "."
                            : "Campagne de dépôt fermée.", null, null);
                    for (Memoire m : new com.tdsi.memotheque.service.MemoireService().mesMemoires()) {
                        if (m.getStatut() == com.tdsi.memotheque.model.StatutMemoire.REFUSE) {
                            ajouterAction("Corrections demandées sur « " + m.getTitre() + " ».", "Corriger", "mes_memoires");
                        }
                    }
                }
                case ENCADREUR -> {
                    int n = new com.tdsi.memotheque.service.ValidationService().listerEnAttente().size();
                    ajouterAction(n == 0 ? "Aucun mémoire en attente de votre avis."
                            : n + " mémoire(s) en attente de votre avis.", n == 0 ? null : "Évaluer", "validation");
                }
                case ADMIN -> {
                    int aPlanifier = soutenances.aPlanifier().size();
                    long pv = soutenances.soutenances().stream()
                            .filter(s -> s.getStatut() == com.tdsi.memotheque.model.Soutenance.Statut.PLANIFIEE
                                    && s.getDateHeure().isBefore(maintenant)).count();
                    ajouterAction(aPlanifier + " soutenance(s) à planifier, " + pv + " procès-verbal(aux) à saisir.",
                            aPlanifier + pv == 0 ? null : "Ouvrir", "soutenances");
                    com.tdsi.memotheque.service.ParametreService.Campagne c =
                            new com.tdsi.memotheque.service.ParametreService().campagne();
                    ajouterAction("Année " + c.anneeCourante() + " — dépôts "
                            + (c.depotPossible(java.time.LocalDate.now()) ? "ouverts" : "fermés") + ".",
                            "Paramétrer", "parametres");
                }
            }
            soutenances.soutenances().stream()
                    .filter(s -> s.getStatut() == com.tdsi.memotheque.model.Soutenance.Statut.PLANIFIEE
                            && s.getDateHeure().isAfter(maintenant))
                    .sorted(java.util.Comparator.comparing(com.tdsi.memotheque.model.Soutenance::getDateHeure))
                    .limit(5)
                    .forEach(s -> ajouterAction("Soutenance le " + s.getDateHeureLisible() + " (" + s.getSalle()
                            + ") : « " + s.getTitre() + " », " + s.getEtudiant() + ".",
                            u.getRole() == com.tdsi.memotheque.model.Role.ETUDIANT ? "Ma convocation" : "Convocations",
                            u.getRole() == com.tdsi.memotheque.model.Role.ETUDIANT ? "mes_memoires" : "soutenances"));
        } catch (SQLException | SecurityException e) {
            ajouterAction("Informations indisponibles : " + e.getMessage(), null, null);
        }
    }

    private void ajouterAction(String texte, String bouton, String vue) {
        Label l = new Label(texte);
        l.setGraphic((texte.startsWith("Soutenance") ? com.tdsi.memotheque.util.Icone.CALENDRIER
                : com.tdsi.memotheque.util.Icone.DOCUMENT).creer(16));
        l.setGraphicTextGap(10);
        l.setWrapText(true);
        if (bouton == null) {
            blocAFaire.getChildren().add(l);
            return;
        }
        l.setWrapText(true);
        javafx.scene.control.Hyperlink lien = new javafx.scene.control.Hyperlink(bouton);
        lien.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        lien.setOnAction(e -> SceneNavigator.charger(vue));
        javafx.scene.layout.HBox ligne = new javafx.scene.layout.HBox(6, l, lien);
        ligne.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        blocAFaire.getChildren().add(ligne);
    }

    private void remplirDomaines(Map<String, Integer> donnees) {
        graphiqueDomaines.getData().clear();
        donnees.forEach((domaine, nb) ->
                graphiqueDomaines.getData().add(new PieChart.Data(domaine + " (" + nb + ")", nb)));
    }

    private void remplirAnnees(Map<String, Integer> donnees) {
        XYChart.Series<String, Number> serie = new XYChart.Series<>();
        serie.setName("Mémoires publiés");
        donnees.forEach((annee, nb) -> serie.getData().add(new XYChart.Data<>(annee, nb)));

        // Axe en nombres entiers : de 0 au maximum arrondi, graduation lisible.
        int max = donnees.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int haut = Math.max(5, max + 1);
        axeNombre.setUpperBound(haut);
        axeNombre.setTickUnit(Math.max(1, Math.ceil(haut / 10.0)));
        axeNombre.setTickLabelFormatter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Number n) {
                return n.doubleValue() == Math.rint(n.doubleValue()) ? String.valueOf(n.intValue()) : "";
            }

            @Override
            public Number fromString(String s) {
                return Integer.valueOf(s);
            }
        });

        graphiqueAnnees.getData().clear();
        graphiqueAnnees.getData().add(serie);
    }
}

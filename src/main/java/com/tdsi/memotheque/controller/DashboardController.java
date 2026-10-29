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

package com.tdsi.memotheque.controller;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.tdsi.memotheque.model.Niveau;
import com.tdsi.memotheque.service.ParametreService;
import com.tdsi.memotheque.util.Dates;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.GridPane;

/**
 * Campagne de dépôt (administration) : année en cours, et pour chaque niveau l'ouverture,
 * la date limite, et des raccourcis pour prolonger ou écourter le délai.
 */
public class ParametresController {

    @FXML private TextField champAnnee;
    @FXML private GridPane grilleNiveaux;
    @FXML private Label labelMessage;
    @FXML private Button boutonEnregistrer;

    private final ParametreService service = new ParametreService();

    /** Ligne de réglage d'un niveau. */
    private record Ligne(CheckBox ouverte, DatePicker limite, Label etat) { }

    private final Map<Niveau, Ligne> lignes = new EnumMap<>(Niveau.class);

    @FXML
    private void initialize() {
        construireGrille();
        charger();
    }

    private void construireGrille() {
        String[] titres = {"Niveau", "Dépôts", "Date limite", "Ajuster le délai", "État actuel"};
        for (int i = 0; i < titres.length; i++) {
            Label t = new Label(titres[i]);
            t.getStyleClass().add("champ-titre");
            grilleNiveaux.add(t, i, 0);
        }
        int r = 1;
        for (Niveau n : Niveau.values()) {
            Label nom = new Label(n.name());
            nom.getStyleClass().add("sous-titre");
            CheckBox ouverte = new CheckBox("Ouverts");
            DatePicker limite = Dates.preparer(new DatePicker());
            limite.setPrefWidth(170);
            Label etat = new Label();
            etat.getStyleClass().add("pastille");
            Button moins = new Button("− 7 jours");
            Button plus = new Button("+ 7 jours");
            moins.setOnAction(e -> decaler(n, -7));
            plus.setOnAction(e -> decaler(n, 7));
            javafx.scene.layout.HBox delai = new javafx.scene.layout.HBox(8, moins, plus);
            ouverte.selectedProperty().addListener((o, a, b) -> limite.setDisable(!b));
            grilleNiveaux.addRow(r++, nom, ouverte, limite, delai, etat);
            lignes.put(n, new Ligne(ouverte, limite, etat));
        }
    }

    @FXML
    private void recharger() {
        charger();
        labelMessage.setText("Modifications annulées.");
    }

    private void charger() {
        try {
            ParametreService.Campagne g = service.campagne();
            champAnnee.setText(g.anneeCourante());
            LocalDate aujourdhui = LocalDate.now();
            for (Map.Entry<Niveau, ParametreService.Campagne> e : service.campagnes().entrySet()) {
                Ligne l = lignes.get(e.getKey());
                ParametreService.Campagne c = e.getValue();
                l.ouverte().setSelected(c.ouverte());
                l.limite().setValue(c.dateLimite());
                l.limite().getEditor().setText(Dates.format(c.dateLimite()));
                l.limite().setDisable(!c.ouverte());
                l.etat().setText(c.etat(aujourdhui));
                l.etat().getStyleClass().removeAll("valide", "refuse", "en_attente");
                l.etat().getStyleClass().add(c.depotPossible(aujourdhui) ? "valide" : c.ouverte() ? "en_attente" : "refuse");
            }
        } catch (SQLException e) {
            labelMessage.setText("Impossible de lire les paramètres : " + e.getMessage());
        }
    }

    /** Prolonge ou écourte la date affichée (à enregistrer ensuite). */
    private void decaler(Niveau n, int jours) {
        Ligne l = lignes.get(n);
        try {
            LocalDate base = Dates.lire(l.limite());
            LocalDate nouvelle = (base == null ? LocalDate.now() : base).plusDays(jours);
            l.ouverte().setSelected(true);
            l.limite().setValue(nouvelle);
            l.limite().getEditor().setText(Dates.format(nouvelle));
            labelMessage.setText(n + " : nouvelle date limite " + Dates.format(nouvelle) + " — cliquez sur Enregistrer pour l'appliquer.");
        } catch (IllegalArgumentException e) {
            labelMessage.setText(e.getMessage());
        }
    }

    @FXML
    private void toutOuvrir() {
        lignes.values().forEach(l -> l.ouverte().setSelected(true));
    }

    @FXML
    private void toutFermer() {
        lignes.values().forEach(l -> l.ouverte().setSelected(false));
    }

    @FXML
    private void memeDate() {
        TextInputDialog d = new TextInputDialog(Dates.format(LocalDate.now().plusMonths(1)));
        d.setTitle("Même date limite partout");
        d.setHeaderText("Date limite à appliquer à tous les niveaux ouverts (jj/mm/aaaa) :");
        d.showAndWait().ifPresent(t -> {
            try {
                LocalDate date = Dates.analyser(t);
                for (Ligne l : lignes.values()) {
                    if (l.ouverte().isSelected()) {
                        l.limite().setValue(date);
                        l.limite().getEditor().setText(Dates.format(date));
                    }
                }
                labelMessage.setText("Date appliquée aux niveaux ouverts — cliquez sur Enregistrer.");
            } catch (IllegalArgumentException e) {
                labelMessage.setText(e.getMessage());
            }
        });
    }

    @FXML
    private void proposerAnnee() {
        champAnnee.setText(ParametreService.anneeParDefaut(LocalDate.now()));
    }

    @FXML
    private void enregistrer() {
        try {
            // On lit d'abord toutes les dates (texte compris) : une erreur n'enregistre rien.
            Map<Niveau, LocalDate> dates = new EnumMap<>(Niveau.class);
            for (Map.Entry<Niveau, Ligne> e : lignes.entrySet()) {
                try {
                    dates.put(e.getKey(), Dates.lire(e.getValue().limite()));
                } catch (IllegalArgumentException ex) {
                    throw new IllegalArgumentException("Niveau " + e.getKey() + " : " + ex.getMessage());
                }
            }
            service.definirAnnee(champAnnee.getText());
            List<String> changements = new ArrayList<>();
            for (Map.Entry<Niveau, Ligne> e : lignes.entrySet()) {
                String c = service.definirCampagne(e.getKey(), e.getValue().ouverte().isSelected(), dates.get(e.getKey()));
                if (c != null) {
                    changements.add(Character.toUpperCase(c.charAt(0)) + c.substring(1));
                }
            }
            charger();
            labelMessage.setText(changements.isEmpty() ? "Paramètres enregistrés (aucun changement de campagne)."
                    : "Enregistré. Étudiants prévenus :\n• " + String.join("\n• ", changements));
            if (!changements.isEmpty()) {
                com.tdsi.memotheque.util.AlertUtil.info("Campagne de dépôt", labelMessage.getText());
            }
        } catch (IllegalArgumentException | SecurityException e) {
            labelMessage.setText(e.getMessage());
        } catch (SQLException e) {
            labelMessage.setText("Erreur d'enregistrement : " + e.getMessage());
        }
    }
}

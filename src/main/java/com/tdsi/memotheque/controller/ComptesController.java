package com.tdsi.memotheque.controller;

import java.sql.SQLException;

import com.tdsi.memotheque.model.Role;
import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.service.CompteService;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

/** [M6] (Admin) Liste des comptes : activer, désactiver, déverrouiller, créer un encadreur. */
public class ComptesController {

    @FXML private TableView<Utilisateur> tableComptes;
    @FXML private TableColumn<Utilisateur, String> colNom;
    @FXML private TableColumn<Utilisateur, String> colEmail;
    @FXML private TableColumn<Utilisateur, String> colMatricule;
    @FXML private TableColumn<Utilisateur, Role> colRole;
    @FXML private TableColumn<Utilisateur, Object> colNiveau;
    @FXML private TableColumn<Utilisateur, Boolean> colActif;
    @FXML private TableColumn<Utilisateur, Boolean> colVerrouille;
    @FXML private CheckBox caseInactifs;
    @FXML private Button boutonActiver;
    @FXML private Button boutonDesactiver;
    @FXML private Button boutonDeverrouiller;
    @FXML private Button boutonReinitialiser;
    @FXML private TextField champNom;
    @FXML private TextField champPrenom;
    @FXML private TextField champEmail;
    @FXML private PasswordField champMotDePasse;
    @FXML private Label labelMessage;

    private final CompteService compteService = new CompteService();
    private final ObservableList<Utilisateur> comptes = FXCollections.observableArrayList();
    private final FilteredList<Utilisateur> comptesFiltres = new FilteredList<>(comptes, u -> true);

    @FXML
    private void initialize() {
        colNom.setCellValueFactory(new PropertyValueFactory<>("nomComplet"));
        colEmail.setCellValueFactory(new PropertyValueFactory<>("email"));
        colMatricule.setCellValueFactory(new PropertyValueFactory<>("matricule"));
        colRole.setCellValueFactory(new PropertyValueFactory<>("role"));
        colNiveau.setCellValueFactory(new PropertyValueFactory<>("niveau"));
        colActif.setCellValueFactory(new PropertyValueFactory<>("actif"));
        colVerrouille.setCellValueFactory(new PropertyValueFactory<>("verrouille"));
        colActif.setCellFactory(c -> celluleOuiNon("Actif", "Inactif", "statut-valide", "statut-en_attente"));
        colVerrouille.setCellFactory(c -> celluleOuiNon("Verrouillé", "Non", "statut-refuse", ""));

        tableComptes.setItems(comptesFiltres);
        tableComptes.getSelectionModel().selectedItemProperty()
                .addListener((obs, ancien, nouveau) -> majBoutons(nouveau));
        caseInactifs.selectedProperty().addListener((obs, ancien, coche) ->
                comptesFiltres.setPredicate(u -> !coche || !u.isActif() || u.isVerrouille()));

        majBoutons(null);
        charger();
    }

    private TableCell<Utilisateur, Boolean> celluleOuiNon(String oui, String non,
            String styleOui, String styleNon) {
        return new TableCell<>() {
            @Override
            protected void updateItem(Boolean valeur, boolean vide) {
                super.updateItem(valeur, vide);
                getStyleClass().removeAll(styleOui, styleNon);
                if (vide || valeur == null) {
                    setText(null);
                    return;
                }
                setText(valeur ? oui : non);
                String style = valeur ? styleOui : styleNon;
                if (!style.isEmpty()) {
                    getStyleClass().add(style);
                }
            }
        };
    }

    private void charger() {
        try {
            comptes.setAll(compteService.listerComptes());
        } catch (SQLException | SecurityException e) {
            labelMessage.setText("Impossible de charger les comptes : " + e.getMessage());
        }
    }

    private void majBoutons(Utilisateur u) {
        boutonActiver.setDisable(u == null || u.isActif());
        boutonDesactiver.setDisable(u == null || !u.isActif());
        boutonDeverrouiller.setDisable(u == null || (!u.isVerrouille() && u.getTentativesEchec() == 0));
        boutonReinitialiser.setDisable(u == null);
    }

    @FXML
    private void activer() {
        executer(u -> compteService.activer(u.getId()), "Compte activé.");
    }

    @FXML
    private void desactiver() {
        executer(u -> compteService.desactiver(u.getId()), "Compte désactivé.");
    }

    @FXML
    private void deverrouiller() {
        executer(u -> compteService.deverrouiller(u.getId()), "Compte déverrouillé.");
    }

    @FXML
    private void reinitialiser() {
        Utilisateur u = tableComptes.getSelectionModel().getSelectedItem();
        if (u == null || !com.tdsi.memotheque.util.AlertUtil.confirmer("Réinitialisation",
                "Générer un mot de passe temporaire pour " + u.getNomComplet() + " ?\n"
                + "Il devra le changer à sa prochaine connexion.")) {
            return;
        }
        try {
            String temporaire = compteService.reinitialiserMotDePasse(u.getId());
            charger();
            afficherMotDePasseTemporaire(u, temporaire);
            labelMessage.setText("Mot de passe réinitialisé pour " + u.getEmail() + ".");
        } catch (IllegalArgumentException | SecurityException e) {
            labelMessage.setText(e.getMessage());
        } catch (SQLException e) {
            labelMessage.setText("Erreur : " + e.getMessage());
        }
    }

    /** Affiché une seule fois : le mot de passe temporaire n'est stocké nulle part en clair. */
    private void afficherMotDePasseTemporaire(Utilisateur u, String temporaire) {
        TextField champ = new TextField(temporaire);
        champ.setEditable(false);
        champ.setStyle("-fx-font-family: monospace; -fx-font-size: 16px;");

        javafx.scene.control.Alert alerte = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.INFORMATION);
        alerte.setTitle("Mot de passe temporaire");
        alerte.setHeaderText("Communiquez ce mot de passe à " + u.getNomComplet()
                + " (il ne sera plus affiché) :");
        alerte.getDialogPane().setContent(champ);
        javafx.scene.input.ClipboardContent contenu = new javafx.scene.input.ClipboardContent();
        contenu.putString(temporaire);
        javafx.scene.input.Clipboard.getSystemClipboard().setContent(contenu);
        alerte.showAndWait();
    }

    @FXML
    private void creerEncadreur() {
        try {
            Utilisateur u = compteService.creerEncadreur(champNom.getText(), champPrenom.getText(),
                    champEmail.getText(), champMotDePasse.getText());
            champNom.clear();
            champPrenom.clear();
            champEmail.clear();
            champMotDePasse.clear();
            charger();
            labelMessage.setText("Compte encadreur créé : " + u.getEmail());
        } catch (IllegalArgumentException | SecurityException e) {
            labelMessage.setText(e.getMessage());
        } catch (SQLException e) {
            labelMessage.setText("Erreur lors de la création : " + e.getMessage());
        }
    }

    private interface ActionCompte {
        void appliquer(Utilisateur u) throws SQLException;
    }

    private void executer(ActionCompte action, String succes) {
        Utilisateur u = tableComptes.getSelectionModel().getSelectedItem();
        if (u == null) {
            return;
        }
        try {
            action.appliquer(u);
            charger();
            labelMessage.setText(succes);
        } catch (IllegalArgumentException | SecurityException e) {
            labelMessage.setText(e.getMessage());
        } catch (SQLException e) {
            labelMessage.setText("Erreur : " + e.getMessage());
        }
    }
}

package com.tdsi.memotheque.controller;

import java.io.File;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

import com.tdsi.memotheque.model.Niveau;
import com.tdsi.memotheque.model.Role;
import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.service.CompteService;
import com.tdsi.memotheque.util.AlertUtil;
import com.tdsi.memotheque.util.Icone;
import com.tdsi.memotheque.util.TacheFond;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/**
 * [M6] (Admin) Comptes : étudiants, enseignants, personnel administratif.
 * Création à l'unité (formulaire adapté au type) ou import d'un fichier Excel / CSV ;
 * activation, désactivation, déverrouillage, mot de passe temporaire.
 */
public class ComptesController {

    @FXML private TableView<Utilisateur> tableComptes;
    @FXML private TableColumn<Utilisateur, String> colNom;
    @FXML private TableColumn<Utilisateur, String> colEmail;
    @FXML private TableColumn<Utilisateur, String> colTelephone;
    @FXML private TableColumn<Utilisateur, String> colMatricule;
    @FXML private TableColumn<Utilisateur, Role> colRole;
    @FXML private TableColumn<Utilisateur, Object> colNiveau;
    @FXML private TableColumn<Utilisateur, Utilisateur> colActif;
    @FXML private CheckBox caseInactifs;
    @FXML private ToggleButton ongletTous;
    @FXML private ToggleButton ongletEtudiants;
    @FXML private ToggleButton ongletEnseignants;
    @FXML private ToggleButton ongletAdministratifs;
    @FXML private TextField champRecherche;
    @FXML private Button boutonNouveau;
    @FXML private Button boutonImporter;
    @FXML private Button boutonActiver;
    @FXML private Button boutonDesactiver;
    @FXML private Button boutonDeverrouiller;
    @FXML private Button boutonReinitialiser;
    @FXML private Button boutonRetirer2fa;
    private final com.tdsi.memotheque.service.DoubleFacteurService doubleFacteur =
            new com.tdsi.memotheque.service.DoubleFacteurService();
    @FXML private Label labelMessage;

    private final CompteService compteService = new CompteService();
    private final ObservableList<Utilisateur> comptes = FXCollections.observableArrayList();
    private final FilteredList<Utilisateur> comptesFiltres = new FilteredList<>(comptes, u -> true);
    private final ToggleGroup onglets = new ToggleGroup();

    @FXML
    private void initialize() {
        colNom.setCellValueFactory(new PropertyValueFactory<>("nomComplet"));
        colEmail.setCellValueFactory(new PropertyValueFactory<>("email"));
        colTelephone.setCellValueFactory(new PropertyValueFactory<>("telephone"));
        colMatricule.setCellValueFactory(new PropertyValueFactory<>("matricule"));
        colRole.setCellValueFactory(new PropertyValueFactory<>("role"));
        colNiveau.setCellValueFactory(new PropertyValueFactory<>("niveau"));
        colActif.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue()));
        colActif.setCellFactory(c -> celluleEtat());

        boutonNouveau.setGraphic(Icone.COMPTES.creer(18));
        boutonImporter.setGraphic(Icone.SAUVEGARDE.creer(18));
        for (ToggleButton t : List.of(ongletTous, ongletEtudiants, ongletEnseignants, ongletAdministratifs)) {
            t.setToggleGroup(onglets);
        }
        onglets.selectedToggleProperty().addListener((o, a, n) -> {
            if (n == null) {
                a.setSelected(true);       // toujours un onglet actif
            }
            filtrer();
        });
        champRecherche.textProperty().addListener((o, a, n) -> filtrer());
        caseInactifs.selectedProperty().addListener((o, a, n) -> filtrer());

        tableComptes.setItems(comptesFiltres);
        tableComptes.getSelectionModel().selectedItemProperty()
                .addListener((obs, ancien, nouveau) -> majBoutons(nouveau));

        majBoutons(null);
        charger();
    }

    private Role roleOnglet() {
        if (ongletEtudiants.isSelected()) {
            return Role.ETUDIANT;
        }
        if (ongletEnseignants.isSelected()) {
            return Role.ENCADREUR;
        }
        return ongletAdministratifs.isSelected() ? Role.ADMIN : null;
    }

    private void filtrer() {
        Role role = roleOnglet();
        String q = champRecherche.getText() == null ? "" : champRecherche.getText().trim().toLowerCase(Locale.ROOT);
        boolean inactifs = caseInactifs.isSelected();
        comptesFiltres.setPredicate(u -> (role == null || u.getRole() == role)
                && (!inactifs || !u.isActif() || u.isVerrouille())
                && (q.isEmpty() || (u.getNomComplet() + " " + u.getEmail() + " " + nul(u.getTelephone()) + " "
                        + nul(u.getMatricule())).toLowerCase(Locale.ROOT).contains(q)));
    }

    private static String nul(String s) {
        return s == null ? "" : s;
    }

    /** Compteurs dans les onglets : « Étudiants (9) ». */
    private void compter() {
        long e = comptes.stream().filter(u -> u.getRole() == Role.ETUDIANT).count();
        long p = comptes.stream().filter(u -> u.getRole() == Role.ENCADREUR).count();
        long a = comptes.stream().filter(u -> u.getRole() == Role.ADMIN).count();
        ongletTous.setText("Tous (" + comptes.size() + ")");
        ongletEtudiants.setText("Étudiants (" + e + ")");
        ongletEnseignants.setText("Enseignants (" + p + ")");
        ongletAdministratifs.setText("Personnel administratif (" + a + ")");
    }

    /** État du compte en pastille : Verrouillé (prioritaire), Actif ou Inactif. */
    private TableCell<Utilisateur, Utilisateur> celluleEtat() {
        return new TableCell<>() {
            @Override
            protected void updateItem(Utilisateur u, boolean vide) {
                super.updateItem(u, vide);
                setText(null);
                if (vide || u == null) {
                    setGraphic(null);
                    return;
                }
                Label l = new Label(u.isVerrouille() ? "Verrouillé" : u.isActif() ? "Actif" : "Inactif");
                l.getStyleClass().addAll("pastille", u.isVerrouille() ? "refuse" : u.isActif() ? "valide" : "en_attente");
                setGraphic(l);
            }
        };
    }

    private void charger() {
        try {
            comptes.setAll(compteService.listerComptes());
            compter();
            filtrer();
        } catch (SQLException | SecurityException e) {
            labelMessage.setText("Impossible de charger les comptes : " + e.getMessage());
        }
    }

    private void majBoutons(Utilisateur u) {
        boutonActiver.setDisable(u == null || u.isActif());
        boutonDesactiver.setDisable(u == null || !u.isActif());
        boutonDeverrouiller.setDisable(u == null || (!u.isVerrouille() && u.getTentativesEchec() == 0));
        boutonReinitialiser.setDisable(u == null);
        boolean deuxFa;
        try {
            deuxFa = u != null && doubleFacteur.estActive(u.getId());
        } catch (SQLException e) {
            deuxFa = false;
        }
        boutonRetirer2fa.setDisable(!deuxFa);
    }

    /** Téléphone perdu ou changé : la personne pourra se connecter avec son seul mot de passe, puis réactiver. */
    @FXML
    private void retirerDoubleFacteur() {
        Utilisateur u = tableComptes.getSelectionModel().getSelectedItem();
        if (u == null || !com.tdsi.memotheque.util.AlertUtil.confirmer("Double authentification",
                "Retirer la double authentification du compte de " + u.getNomComplet() + " ?\n"
                + "À faire seulement après avoir vérifié l'identité de la personne (téléphone perdu).")) {
            return;
        }
        executer(x -> doubleFacteur.retirer(x.getId()), "Double authentification retirée pour " + u.getEmail() + ".");
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

    // ------------------------------------------------------------------ création à l'unité

    /** Formulaire adapté au type : n° de carte étudiant et niveau seulement pour un étudiant. */
    @FXML
    private void nouveauCompte() {
        ComboBox<Role> type = new ComboBox<>(FXCollections.observableArrayList(Role.ETUDIANT, Role.ENCADREUR, Role.ADMIN));
        Role parDefaut = roleOnglet();
        type.setValue(parDefaut == null ? Role.ETUDIANT : parDefaut);
        type.setMaxWidth(Double.MAX_VALUE);
        TextField prenom = new TextField();
        TextField nom = new TextField();
        TextField email = new TextField();
        email.setPromptText("prenom.nom@ucad.edu.sn");
        TextField telephone = new TextField();
        telephone.setPromptText("+221 77 123 45 67 (facultatif)");
        TextField carte = new TextField();
        carte.setPromptText("ex. MCS-2026-010");
        ComboBox<Niveau> niveau = new ComboBox<>(FXCollections.observableArrayList(Niveau.values()));
        niveau.setMaxWidth(Double.MAX_VALUE);
        Label lCarte = new Label("N° carte étudiant *");
        Label lNiveau = new Label("Niveau *");
        Label erreur = new Label();
        erreur.getStyleClass().add("message-erreur");
        erreur.setWrapText(true);
        erreur.setMaxWidth(460);

        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(10);
        g.addRow(0, new Label("Type de compte *"), type);
        g.addRow(1, new Label("Prénom *"), prenom);
        g.addRow(2, new Label("Nom *"), nom);
        g.addRow(3, new Label("E-mail *"), email);
        g.addRow(4, new Label("Téléphone"), telephone);
        g.addRow(5, lCarte, carte);
        g.addRow(6, lNiveau, niveau);
        g.add(erreur, 0, 7, 2, 1);
        for (javafx.scene.Node n : List.of(prenom, nom, email, telephone, carte)) {
            ((TextField) n).setPrefWidth(320);
        }
        Runnable majType = () -> {
            boolean etu = type.getValue() == Role.ETUDIANT;
            for (javafx.scene.Node n : List.of(lCarte, carte, lNiveau, niveau)) {
                n.setVisible(etu);
                n.setManaged(etu);
            }
        };
        type.valueProperty().addListener((o, a, n) -> majType.run());
        majType.run();

        ButtonType creer = new ButtonType("Créer le compte", ButtonBar.ButtonData.OK_DONE);
        Dialog<CompteService.CompteCree> d = new Dialog<>();
        d.setTitle("Nouveau compte");
        d.setHeaderText("Un mot de passe temporaire sera généré ; la personne le changera à sa première connexion.");
        d.getDialogPane().setContent(g);
        d.getDialogPane().getButtonTypes().addAll(creer, ButtonType.CANCEL);
        CompteService.CompteCree[] cree = new CompteService.CompteCree[1];
        d.getDialogPane().lookupButton(creer).addEventFilter(ActionEvent.ACTION, ev -> {
            try {
                cree[0] = compteService.creerCompte(type.getValue(), prenom.getText(), nom.getText(), email.getText(),
                        telephone.getText(), carte.getText(), niveau.getValue());
            } catch (IllegalArgumentException | SecurityException e) {
                erreur.setText(e.getMessage());
                ev.consume();
            } catch (SQLException e) {
                erreur.setText("Erreur de base de données : " + e.getMessage());
                ev.consume();
            }
        });
        d.setResultConverter(b -> b == creer ? cree[0] : null);
        d.showAndWait().ifPresent(c -> {
            charger();
            labelMessage.setText(c.utilisateur().getRole().getLibelle() + " créé : " + c.utilisateur().getNomComplet()
                    + " (" + c.utilisateur().getEmail() + ").");
            afficherMotDePasseTemporaire(c.utilisateur(), c.motDePasseTemporaire());
        });
    }

    // ------------------------------------------------------------------ import Excel / CSV

    /**
     * Import d'un fichier Excel (.xlsx) ou CSV pour un type de compte. Colonnes reconnues par leur en-tête :
     * Prénom, Nom, N° carte étudiant, Téléphone, Email, Niveau. Un modèle Excel peut être téléchargé.
     */
    @FXML
    private void importer() {
        ComboBox<Role> type = new ComboBox<>(FXCollections.observableArrayList(Role.ETUDIANT, Role.ENCADREUR, Role.ADMIN));
        Role parDefaut = roleOnglet();
        type.setValue(parDefaut == null ? Role.ETUDIANT : parDefaut);
        type.setMaxWidth(Double.MAX_VALUE);
        ComboBox<Niveau> niveau = new ComboBox<>(FXCollections.observableArrayList(Niveau.values()));
        niveau.setPromptText("Pris dans la colonne « Niveau »");
        niveau.setMaxWidth(Double.MAX_VALUE);
        Label lNiveau = new Label("Niveau par défaut");
        Label colonnes = new Label();
        colonnes.setWrapText(true);
        colonnes.setMaxWidth(520);
        colonnes.getStyleClass().add("aide");
        Label fichierChoisi = new Label("Aucun fichier choisi");
        File[] fichier = new File[1];

        Button modele = new Button("Télécharger le modèle Excel");
        modele.setGraphic(Icone.DOCUMENT.creer(16));
        modele.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setInitialFileName("modele-" + (type.getValue() == Role.ETUDIANT ? "etudiants"
                    : type.getValue() == Role.ENCADREUR ? "enseignants" : "personnel-administratif") + ".xlsx");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Classeur Excel", "*.xlsx"));
            File f = fc.showSaveDialog(modele.getScene().getWindow());
            if (f != null) {
                try {
                    CompteService.ecrireModele(type.getValue(), f.toPath());
                    fichierChoisi.setText("Modèle enregistré : " + f.getName() + ". Remplissez-le puis choisissez-le ici.");
                } catch (java.io.IOException ex) {
                    fichierChoisi.setText("Modèle non enregistré : " + ex.getMessage());
                }
            }
        });
        Button choisir = new Button("Choisir le fichier...");
        choisir.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Fichier des comptes");
            fc.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Excel ou CSV", "*.xlsx", "*.csv"),
                    new FileChooser.ExtensionFilter("Tous les fichiers", "*.*"));
            File f = fc.showOpenDialog(choisir.getScene().getWindow());
            if (f != null) {
                fichier[0] = f;
                fichierChoisi.setText(f.getName());
            }
        });

        Runnable majType = () -> {
            boolean etu = type.getValue() == Role.ETUDIANT;
            lNiveau.setVisible(etu);
            lNiveau.setManaged(etu);
            niveau.setVisible(etu);
            niveau.setManaged(etu);
            colonnes.setText(etu
                    ? "Colonnes attendues (1re ligne) : Prénom · Nom · N° carte étudiant · Téléphone · Email · Niveau. "
                            + "Si la colonne Niveau est absente, le niveau par défaut est appliqué."
                    : "Colonnes attendues (1re ligne) : Prénom · Nom · Téléphone · Email.");
        };
        type.valueProperty().addListener((o, a, n) -> majType.run());
        majType.run();

        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(10);
        g.addRow(0, new Label("Type de comptes *"), type);
        g.addRow(1, lNiveau, niveau);
        g.add(colonnes, 0, 2, 2, 1);
        g.add(new HBox(10, modele), 0, 3, 2, 1);
        HBox ligneFichier = new HBox(10, choisir, fichierChoisi);
        ligneFichier.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        g.add(ligneFichier, 0, 4, 2, 1);
        Label erreur = new Label();
        erreur.getStyleClass().add("message-erreur");
        g.add(erreur, 0, 5, 2, 1);

        ButtonType lancer = new ButtonType("Importer", ButtonBar.ButtonData.OK_DONE);
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("Importer des comptes");
        d.setHeaderText("Importer un fichier Excel (.xlsx) ou CSV");
        d.getDialogPane().setContent(new VBox(g));
        d.getDialogPane().getButtonTypes().addAll(lancer, ButtonType.CANCEL);
        d.getDialogPane().lookupButton(lancer).addEventFilter(ActionEvent.ACTION, ev -> {
            if (fichier[0] == null) {
                erreur.setText("Choisissez d'abord le fichier à importer.");
                ev.consume();
            }
        });
        if (d.showAndWait().filter(b -> b == lancer).isEmpty()) {
            return;
        }
        Role role = type.getValue();
        Niveau niveauDefaut = niveau.getValue();
        labelMessage.setText("Import en cours (hachage des mots de passe)...");
        TacheFond.executer(() -> compteService.importerFichier(role, niveauDefaut, fichier[0].toPath()), r -> {
            charger();
            long crees = r.stream().filter(l -> l.resultat().equals("CREE")).count();
            long erreurs = r.stream().filter(l -> l.resultat().equals("ERREUR")).count();
            labelMessage.setText(role.getLibelle() + " : " + crees + " compte(s) créé(s), " + (r.size() - crees - erreurs)
                    + " mis à jour, " + erreurs + " ligne(s) en erreur.");
            AlertUtil.afficherTableau("Résultat de l'import",
                    new String[] {"Ligne", "Personne", "Résultat", "Mot de passe temporaire / détail"},
                    r.stream().map(l -> new String[] {String.valueOf(l.numero()), l.identifiant(), libelle(l.resultat()),
                            l.detail()}).toList());
            if (crees > 0 && AlertUtil.confirmer("Mots de passe temporaires",
                    "Enregistrer les identifiants des " + crees + " nouveaux comptes dans un fichier CSV "
                            + "à leur remettre ?\nIls ne pourront plus être affichés ensuite.")) {
                enregistrerIdentifiants(r);
            }
        }, e -> labelMessage.setText(TacheFond.message(e)), boutonImporter);
    }

    private static String libelle(String resultat) {
        return switch (resultat) {
            case "CREE" -> "Créé";
            case "REINSCRIT" -> "Réinscrit";
            case "MIS_A_JOUR" -> "Mis à jour";
            default -> "Erreur";
        };
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

    private void enregistrerIdentifiants(java.util.List<CompteService.LigneImport> r) {
        javafx.stage.FileChooser choix = new javafx.stage.FileChooser();
        choix.setInitialFileName("identifiants-etudiants.csv");
        java.io.File f = choix.showSaveDialog(labelMessage.getScene().getWindow());
        if (f == null) {
            return;
        }
        try {
            com.tdsi.memotheque.util.CsvUtil.ecrire(f.toPath(), new Object[] {"email", "mot_de_passe_temporaire"},
                    r.stream().filter(l -> l.resultat().equals("CREE"))
                            .map(l -> new Object[] {l.identifiant(), l.detail()}).toList());
            labelMessage.setText("Identifiants enregistrés dans " + f.getName()
                    + " : à remettre aux étudiants puis à supprimer.");
        } catch (java.io.IOException e) {
            labelMessage.setText("Enregistrement impossible : " + e.getMessage());
        }
    }
}

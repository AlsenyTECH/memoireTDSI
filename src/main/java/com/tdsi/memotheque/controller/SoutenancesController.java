package com.tdsi.memotheque.controller;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.tdsi.memotheque.model.MembreJury;
import com.tdsi.memotheque.model.Memoire;
import com.tdsi.memotheque.model.Mention;
import com.tdsi.memotheque.model.NiveauDiffusion;
import com.tdsi.memotheque.model.Role;
import com.tdsi.memotheque.model.RoleJury;
import com.tdsi.memotheque.model.Soutenance;
import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.service.SoutenanceService;
import com.tdsi.memotheque.util.AlertUtil;
import com.tdsi.memotheque.util.Impression;
import com.tdsi.memotheque.util.Libelles;
import com.tdsi.memotheque.util.SceneNavigator;
import com.tdsi.memotheque.util.SessionManager;
import com.tdsi.memotheque.util.TacheFond;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/**
 * Soutenances.
 * Administration (scolarité) : mémoires autorisés à planifier, calendrier, planification avec jury,
 * annulation, saisie du procès-verbal (note → mention, signature), convocations et PV imprimables.
 * Enseignant : les soutenances où il siège, avec son rôle, et sa convocation.
 * Les droits sont de toute façon vérifiés par SoutenanceService.
 */
public class SoutenancesController {

    @FXML private Label labelTitre;
    @FXML private Label labelAide;
    @FXML private VBox blocAPlanifier;
    @FXML private TableView<Memoire> tableAPlanifier;
    @FXML private TableColumn<Memoire, String> colPTitre;
    @FXML private TableColumn<Memoire, String> colPEtudiant;
    @FXML private TableColumn<Memoire, Object> colPNiveau;
    @FXML private TableColumn<Memoire, String> colPEncadreur;
    @FXML private TableColumn<Memoire, Object> colPAvis;
    @FXML private Button boutonPlanifier;

    @FXML private TableView<Soutenance> tableSoutenances;
    @FXML private TableColumn<Soutenance, String> colDate;
    @FXML private TableColumn<Soutenance, String> colSalle;
    @FXML private TableColumn<Soutenance, String> colTitre;
    @FXML private TableColumn<Soutenance, String> colEtudiant;
    @FXML private TableColumn<Soutenance, String> colNiveau;
    @FXML private TableColumn<Soutenance, String> colJury;
    @FXML private TableColumn<Soutenance, String> colMonRole;
    @FXML private TableColumn<Soutenance, String> colEtat;
    @FXML private Button boutonReplanifier;
    @FXML private Button boutonAnnuler;
    @FXML private Button boutonPv;
    @FXML private Button boutonConvocation;
    @FXML private Button boutonImprimerPv;
    @FXML private Button boutonDetail;
    @FXML private Label labelMessage;

    private final SoutenanceService service = new SoutenanceService();
    private final ObservableList<Memoire> aPlanifier = FXCollections.observableArrayList();
    private final ObservableList<Soutenance> soutenances = FXCollections.observableArrayList();
    private boolean admin;

    @FXML
    private void initialize() {
        admin = SessionManager.aLeRole(Role.ADMIN);
        if (!admin) {
            labelTitre.setText("Mes soutenances (jury)");
            labelAide.setText("Soutenances où vous siégez. La scolarité fixe la date, la salle et le jury ; "
                    + "le procès-verbal est saisi par la scolarité à l'issue de la soutenance.");
            afficher(blocAPlanifier, false);
            for (Node n : new Node[] {boutonReplanifier, boutonAnnuler, boutonPv}) {
                afficher(n, false);
            }
        }
        colMonRole.setVisible(!admin);

        colPTitre.setCellValueFactory(new PropertyValueFactory<>("titre"));
        colPEtudiant.setCellValueFactory(new PropertyValueFactory<>("auteurNomComplet"));
        colPNiveau.setCellValueFactory(new PropertyValueFactory<>("niveau"));
        colPEncadreur.setCellValueFactory(new PropertyValueFactory<>("encadreurNomComplet"));
        colPAvis.setCellValueFactory(new PropertyValueFactory<>("dateAvis"));
        tableAPlanifier.setItems(aPlanifier);
        tableAPlanifier.getSelectionModel().selectedItemProperty()
                .addListener((o, a, m) -> boutonPlanifier.setDisable(m == null));

        colDate.setCellValueFactory(new PropertyValueFactory<>("dateHeureLisible"));
        colSalle.setCellValueFactory(new PropertyValueFactory<>("salle"));
        colTitre.setCellValueFactory(new PropertyValueFactory<>("titre"));
        colEtudiant.setCellValueFactory(new PropertyValueFactory<>("etudiant"));
        colNiveau.setCellValueFactory(new PropertyValueFactory<>("niveauAnnee"));
        colJury.setCellValueFactory(new PropertyValueFactory<>("juryLisible"));
        colEtat.setCellValueFactory(c -> new SimpleStringProperty(etat(c.getValue())));
        Utilisateur moi = SessionManager.getUtilisateur();
        colMonRole.setCellValueFactory(c -> {
            RoleJury r = moi == null ? null : c.getValue().roleDe(moi.getId());
            return new SimpleStringProperty(r == null ? "" : r.getLibelle());
        });
        tableSoutenances.setItems(soutenances);
        tableSoutenances.getSelectionModel().selectedItemProperty().addListener((o, a, s) -> majBoutons(s));

        majBoutons(null);
        boutonPlanifier.setDisable(true);
        charger();
    }

    private static void afficher(Node n, boolean visible) {
        n.setVisible(visible);
        n.setManaged(visible);
    }

    private static String etat(Soutenance s) {
        if (s.getStatut() == Soutenance.Statut.TENUE) {
            return "Tenue — " + Libelles.resultat(s.getMemoire());
        }
        return s.getDateHeure().isBefore(LocalDateTime.now()) ? "PV à saisir" : "Planifiée";
    }

    private void charger() {
        try {
            if (admin) {
                aPlanifier.setAll(service.aPlanifier());
            }
            List<Soutenance> liste = new ArrayList<>(service.soutenances());
            // À venir d'abord (la plus proche en tête), puis les soutenances tenues.
            liste.sort((a, b) -> {
                boolean ta = a.getStatut() == Soutenance.Statut.TENUE;
                boolean tb = b.getStatut() == Soutenance.Statut.TENUE;
                return ta != tb ? Boolean.compare(ta, tb)
                        : ta ? b.getDateHeure().compareTo(a.getDateHeure()) : a.getDateHeure().compareTo(b.getDateHeure());
            });
            soutenances.setAll(liste);
        } catch (SQLException | SecurityException e) {
            labelMessage.setText("Impossible de charger les soutenances : " + e.getMessage());
        }
    }

    private void majBoutons(Soutenance s) {
        boolean planifiee = s != null && s.getStatut() == Soutenance.Statut.PLANIFIEE;
        boutonReplanifier.setDisable(!planifiee);
        boutonAnnuler.setDisable(!planifiee);
        boutonPv.setDisable(!planifiee || s.getDateHeure().isAfter(LocalDateTime.now()));
        boutonConvocation.setDisable(!planifiee);
        boutonImprimerPv.setDisable(s == null || s.getStatut() != Soutenance.Statut.TENUE);
        boutonDetail.setDisable(s == null);
    }

    // ------------------------------------------------------------------ planification

    @FXML
    private void planifier() {
        Memoire m = tableAPlanifier.getSelectionModel().getSelectedItem();
        if (m != null) {
            dialoguePlanification(m, null);
        }
    }

    @FXML
    private void replanifier() {
        Soutenance s = tableSoutenances.getSelectionModel().getSelectedItem();
        if (s != null) {
            dialoguePlanification(s.getMemoire(), s);
        }
    }

    /** Date, heure, salle, président et examinateurs ; l'encadreur siège d'office. */
    private void dialoguePlanification(Memoire m, Soutenance existante) {
        List<Utilisateur> enseignants;
        try {
            int idEncadreur = m.getEncadreur() == null ? -1 : m.getEncadreur().getId();
            enseignants = service.enseignants().stream().filter(u -> u.getId() != idEncadreur).toList();
        } catch (SQLException e) {
            labelMessage.setText("Impossible de charger les enseignants : " + e.getMessage());
            return;
        }

        DatePicker date = new DatePicker(existante == null ? LocalDate.now().plusDays(7)
                : existante.getDateHeure().toLocalDate());
        TextField heure = new TextField(existante == null ? "09:00"
                : existante.getDateHeure().toLocalTime().toString());
        heure.setPromptText("HH:mm");
        TextField salle = new TextField(existante == null ? "" : existante.getSalle());
        salle.setPromptText("ex. Amphi A, Salle 12");
        ComboBox<Utilisateur> president = new ComboBox<>(FXCollections.observableArrayList(enseignants));
        ComboBox<Utilisateur> examinateur1 = new ComboBox<>(FXCollections.observableArrayList(enseignants));
        ComboBox<Utilisateur> examinateur2 = new ComboBox<>(FXCollections.observableArrayList(enseignants));
        examinateur2.setPromptText("Facultatif");
        for (ComboBox<Utilisateur> c : List.of(president, examinateur1, examinateur2)) {
            c.setMaxWidth(Double.MAX_VALUE);
        }
        if (existante != null) {
            int n = 0;
            for (MembreJury j : existante.getJury()) {
                Utilisateur e = enseignants.stream().filter(u -> u.getId() == j.enseignant().getId())
                        .findFirst().orElse(null);
                if (j.role() == RoleJury.PRESIDENT) {
                    president.setValue(e);
                } else if (j.role() == RoleJury.EXAMINATEUR) {
                    (n++ == 0 ? examinateur1 : examinateur2).setValue(e);
                }
            }
        }
        Label erreur = new Label();
        erreur.getStyleClass().add("message-erreur");
        erreur.setWrapText(true);
        erreur.setMaxWidth(420);

        GridPane g = new GridPane();
        g.setHgap(10);
        g.setVgap(8);
        g.addRow(0, new Label("Mémoire"), new Label(m.getTitre() + " — " + m.getAuteurNomComplet()));
        g.addRow(1, new Label("Date *"), date);
        g.addRow(2, new Label("Heure *"), heure);
        g.addRow(3, new Label("Salle *"), salle);
        g.addRow(4, new Label("Président *"), president);
        g.addRow(5, new Label("Examinateur *"), examinateur1);
        g.addRow(6, new Label("2e examinateur"), examinateur2);
        g.addRow(7, new Label("Encadreur"), new Label(m.getEncadreurNomComplet() + " (membre d'office)"));
        g.add(new Label("Durée : " + SoutenanceService.DUREE_MINUTES + " min. Les conflits de salle et de jury sont contrôlés."),
                0, 8, 2, 1);
        g.add(erreur, 0, 9, 2, 1);

        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle(existante == null ? "Planifier la soutenance" : "Replanifier la soutenance");
        d.getDialogPane().setContent(g);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        // La boîte reste ouverte tant que la planification est refusée (conflit, horaire...).
        d.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(ActionEvent.ACTION, ev -> {
            try {
                if (date.getValue() == null || president.getValue() == null || examinateur1.getValue() == null) {
                    throw new IllegalArgumentException("Date, président et examinateur sont obligatoires.");
                }
                LocalTime h = LocalTime.parse(heure.getText().trim());
                List<Integer> examinateurs = new ArrayList<>();
                examinateurs.add(examinateur1.getValue().getId());
                if (examinateur2.getValue() != null) {
                    examinateurs.add(examinateur2.getValue().getId());
                }
                service.planifier(m.getId(), LocalDateTime.of(date.getValue(), h), salle.getText(),
                        president.getValue().getId(), examinateurs);
            } catch (DateTimeParseException e) {
                erreur.setText("Heure invalide : utilisez le format HH:mm (ex. 09:30).");
                ev.consume();
            } catch (IllegalArgumentException | IllegalStateException | SecurityException e) {
                erreur.setText(e.getMessage());
                ev.consume();
            } catch (SQLException e) {
                erreur.setText("Erreur de base de données : " + e.getMessage());
                ev.consume();
            }
        });
        if (d.showAndWait().filter(b -> b == ButtonType.OK).isPresent()) {
            labelMessage.setText("Soutenance " + (existante == null ? "planifiée" : "replanifiée")
                    + " : l'étudiant et les membres du jury sont notifiés.");
            charger();
        }
    }

    @FXML
    private void annuler() {
        Soutenance s = tableSoutenances.getSelectionModel().getSelectedItem();
        if (s == null) {
            return;
        }
        TextArea raison = new TextArea();
        raison.setPromptText("Motif communiqué à l'étudiant et au jury (facultatif)");
        raison.setPrefRowCount(3);
        raison.setWrapText(true);
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("Annuler la soutenance");
        d.setHeaderText("Annuler la soutenance de « " + s.getTitre() + " » du " + s.getDateHeureLisible() + " ?\n"
                + "Le mémoire reste autorisé à soutenir et revient dans la liste à planifier.");
        d.getDialogPane().setContent(raison);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        if (d.showAndWait().filter(b -> b == ButtonType.OK).isEmpty()) {
            return;
        }
        try {
            service.annuler(s.getIdMemoire(), raison.getText());
            labelMessage.setText("Soutenance annulée.");
            charger();
        } catch (SQLException | IllegalStateException | SecurityException e) {
            labelMessage.setText(e.getMessage());
        }
    }

    // ------------------------------------------------------------------ procès-verbal

    private record SaisiePv(double note, String observations, NiveauDiffusion diffusion, LocalDate embargo,
            String motDePasse) { }

    @FXML
    private void saisirPv() {
        Soutenance s = tableSoutenances.getSelectionModel().getSelectedItem();
        if (s == null) {
            return;
        }
        Memoire m = s.getMemoire();

        TextField note = new TextField();
        note.setPromptText("ex. 14,5");
        Label mention = new Label("Saisissez la note sur 20.");
        mention.getStyleClass().add("valeur-fixe");
        note.textProperty().addListener((o, a, texte) -> {
            Double n = lireNote(texte);
            if (n == null) {
                mention.setText("Note invalide (0 à 20).");
            } else {
                Mention me = SoutenanceService.mentionPour(n);
                mention.setText(me == null ? "Ajourné : le mémoire retourne à l'étudiant pour correction."
                        : "Admis — mention « " + me.getLibelle() + " » : le mémoire sera publié.");
            }
        });
        TextArea observations = new TextArea();
        observations.setPrefRowCount(3);
        observations.setWrapText(true);
        observations.setPromptText("Observations / réserves du jury");
        ComboBox<NiveauDiffusion> diffusion = new ComboBox<>(FXCollections.observableArrayList(NiveauDiffusion.values()));
        diffusion.setValue(m.getNiveauDiffusion() == null ? NiveauDiffusion.PUBLIC : m.getNiveauDiffusion());
        DatePicker embargo = new DatePicker(m.getDateFinEmbargo());
        embargo.disableProperty().bind(diffusion.valueProperty().isNotEqualTo(NiveauDiffusion.CONFIDENTIEL));
        PasswordField motDePasse = new PasswordField();
        motDePasse.setPromptText("Votre mot de passe (signature du PV)");
        Label erreur = new Label();
        erreur.getStyleClass().add("message-erreur");

        GridPane g = new GridPane();
        g.setHgap(10);
        g.setVgap(8);
        g.addRow(0, new Label("Mémoire"), new Label(m.getTitre() + " — " + m.getAuteurNomComplet()));
        g.addRow(1, new Label("Soutenance"), new Label(s.getDateHeureLisible() + ", " + s.getSalle()));
        g.addRow(2, new Label("Jury"), new Label(s.getJuryLisible()));
        g.addRow(3, new Label("Note /20 *"), note);
        g.addRow(4, new Label("Décision"), mention);
        g.addRow(5, new Label("Observations"), observations);
        g.addRow(6, new Label("Diffusion"), diffusion);
        g.addRow(7, new Label("Fin d'embargo"), embargo);
        g.addRow(8, new Label("Mot de passe *"), motDePasse);
        g.add(erreur, 0, 9, 2, 1);

        Dialog<SaisiePv> d = new Dialog<>();
        d.setTitle("Procès-verbal de soutenance");
        d.setHeaderText("Choix de l'étudiant pour la diffusion : "
                + (m.getNiveauDiffusion() == null ? "-" : m.getNiveauDiffusion().getLibelle())
                + ". Le PV publié est signé électroniquement (Ed25519).");
        d.getDialogPane().setContent(g);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        d.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(ActionEvent.ACTION, ev -> {
            if (lireNote(note.getText()) == null) {
                erreur.setText("Note invalide : un nombre entre 0 et 20.");
                ev.consume();
            } else if (motDePasse.getText().isEmpty()) {
                erreur.setText("Confirmez votre mot de passe pour signer.");
                ev.consume();
            }
        });
        d.setResultConverter(b -> b != ButtonType.OK ? null : new SaisiePv(lireNote(note.getText()),
                observations.getText(), diffusion.getValue(), embargo.getValue(), motDePasse.getText()));

        d.showAndWait().ifPresent(pv -> {
            Mention prevue = SoutenanceService.mentionPour(pv.note());
            if (!AlertUtil.confirmer("Procès-verbal", String.format(Locale.FRANCE,
                    "Enregistrer %.2f/20 pour « %s » ?%n%s%nCette décision est définitive.", pv.note(), m.getTitre(),
                    prevue == null ? "Décision : ajourné." : "Décision : admis, mention " + prevue.getLibelle() + "."))) {
                return;
            }
            labelMessage.setText("Vérification du PDF et signature du procès-verbal...");
            TacheFond.executer(() -> service.enregistrerProcesVerbal(s.getIdMemoire(), pv.note(), pv.observations(),
                    pv.diffusion(), pv.embargo(), pv.motDePasse()),
                    resultat -> {
                        labelMessage.setText(resultat.getMention() == null
                                ? "PV enregistré : ajourné, l'étudiant est notifié."
                                : "PV enregistré et signé : mémoire publié avec la mention « "
                                        + resultat.getMention().getLibelle() + " ».");
                        charger();
                    },
                    e -> labelMessage.setText(TacheFond.message(e)), boutonPv);
        });
    }

    /** Accepte « 14,5 » comme « 14.5 ». */
    private static Double lireNote(String texte) {
        try {
            double n = Double.parseDouble(texte.trim().replace(',', '.'));
            return n >= 0 && n <= 20 ? n : null;
        } catch (NumberFormatException | NullPointerException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ documents

    /** Scolarité : une lettre pour l'étudiant et pour chaque membre du jury. Enseignant : la sienne. */
    @FXML
    private void imprimerConvocation() {
        Soutenance s = tableSoutenances.getSelectionModel().getSelectedItem();
        if (s == null) {
            return;
        }
        afficherConvocations(s.getIdMemoire(), labelMessage);
    }

    /** Aperçu et impression des convocations d'un mémoire (droits vérifiés par ConvocationService). */
    static void afficherConvocations(int idMemoire, Label message) {
        try {
            List<com.tdsi.memotheque.service.ConvocationService.Convocation> lettres =
                    new com.tdsi.memotheque.service.ConvocationService().convocations(idMemoire);
            Impression.apercuEtImpressionLettres(lettres.size() > 1 ? "Convocations à la soutenance" : "Ma convocation",
                    lettres.stream().map(Impression::lettre).toList());
        } catch (SQLException | IllegalStateException | SecurityException e) {
            message.setText(e.getMessage());
        }
    }

    @FXML
    private void imprimerPv() {
        Soutenance s = tableSoutenances.getSelectionModel().getSelectedItem();
        if (s == null || s.getStatut() != Soutenance.Statut.TENUE) {
            return;
        }
        Memoire m = s.getMemoire();
        List<String[]> lignes = new ArrayList<>(List.of(
                new String[] {"Candidat(e)", s.getEtudiant()},
                new String[] {"Niveau / année", s.getNiveauAnnee()},
                new String[] {"Mémoire", s.getTitre()},
                new String[] {"Soutenu le", s.getDateHeureLisible() + ", " + s.getSalle()}));
        for (MembreJury j : s.getJury()) {
            lignes.add(new String[] {j.role().getLibelle(), j.enseignant().getNomComplet()});
        }
        lignes.add(new String[] {"Résultat", Libelles.resultat(m)});
        lignes.add(new String[] {"Décision", m.getMention() == null ? "Ajourné" : "Admis"});
        lignes.add(new String[] {"Observations", s.getObservations()});
        if (m.getMention() != null) {
            lignes.add(new String[] {"Diffusion", m.getNiveauDiffusion() == null ? "-" : m.getNiveauDiffusion().getLibelle()});
            lignes.add(new String[] {"Publié le", String.valueOf(m.getDatePublication())});
        }
        Impression.apercuEtImpression("Procès-verbal de soutenance", lignes, m.getMention() == null ? null
                : "PV signé électroniquement (Ed25519) par la scolarité ; la signature est vérifiable depuis la fiche du mémoire.");
    }

    @FXML
    private void voirDetail() {
        Soutenance s = tableSoutenances.getSelectionModel().getSelectedItem();
        if (s != null) {
            SceneNavigator.afficherDetailMemoire(s.getMemoire());
        }
    }
}

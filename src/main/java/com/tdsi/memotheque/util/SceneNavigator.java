package com.tdsi.memotheque.util;

import java.io.IOException;
import java.net.URL;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import com.tdsi.memotheque.controller.DetailMemoireController;
import com.tdsi.memotheque.model.Memoire;


/**
 * [M1] Navigation entre les écrans.
 * - afficherLogin() / afficherCadrePrincipal() : changent la scène de la fenêtre.
 * - charger("recherche") : remplace le contenu central du cadre principal par /view/recherche.fxml.
 */
public final class SceneNavigator {

    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(SceneNavigator.class.getName());

    private static Stage stage;
    private static StackPane zoneContenu;

    private SceneNavigator() { }

    /** Prévenu à chaque changement d'écran (le cadre principal met à jour son menu et son titre). */
    private static java.util.function.Consumer<String> ecouteur;

    public static void init(Stage s) {
        stage = s;
        // Boîtes de dialogue et fenêtres secondaires : même feuille de style et même thème que l'application.
        javafx.stage.Window.getWindows().addListener((javafx.collections.ListChangeListener<javafx.stage.Window>) c -> {
            while (c.next()) {
                for (javafx.stage.Window w : c.getAddedSubList()) {
                    if (w != stage) {
                        habiller(w.getScene());
                        w.sceneProperty().addListener((o, a, scene) -> habiller(scene));
                    }
                }
            }
        });
        for (int taille : new int[] {32, 64, 128, 256}) {
            javafx.scene.image.Image logo = Images.logo(taille);
            if (logo != null) {
                s.getIcons().add(logo);
            }
        }
    }

    private static void habiller(Scene scene) {
        URL css = SceneNavigator.class.getResource("/css/style.css");
        if (scene == null || css == null) {
            return;
        }
        if (!scene.getStylesheets().contains(css.toExternalForm())) {
            scene.getStylesheets().add(0, css.toExternalForm());
        }
        Theme.appliquer(scene);
    }

    public static void surNavigation(java.util.function.Consumer<String> e) { ecouteur = e; }

    private static void prevenir(String vue) {
        if (ecouteur != null) {
            ecouteur.accept(vue);
        }
    }

    public static Stage getStage() { return stage; }

    public static void afficherLogin() {
        zoneContenu = null;
        ecouteur = null;
        changerScene("login", 1040, 660, "MémoTDSI - Connexion");
    }
    
    public static void afficherInscription() {
        zoneContenu = null;
        ecouteur = null;
        changerScene("inscription", 1040, 720, "MémoTDSI - Inscription");
    }

    public static void afficherCadrePrincipal() {
        changerScene("main", 1320, 820, "MémoTDSI");
    }

    /** Appelé par MainController pour indiquer où afficher les écrans. */
    public static void definirZoneContenu(StackPane zone) { zoneContenu = zone; }

    /** Charge un écran (nom du fichier FXML sans extension) dans le cadre principal. */
    public static void charger(String vue) {
        // Garde centrale : un mot de passe temporaire doit être changé avant tout autre écran.
        if (SessionManager.getUtilisateur() != null
                && SessionManager.getUtilisateur().isDoitChangerMotDePasse()) {
            vue = "profil";
        }
        if (zoneContenu == null) {
            AlertUtil.erreur("Navigation", "Le cadre principal n'est pas affiché.");
            return;
        }
        try {
            zoneContenu.getChildren().setAll(chargerFxml(vue));
            prevenir(vue);
        } catch (IOException e) {
            LOG.log(java.util.logging.Level.SEVERE, "Erreur de navigation", e);
            AlertUtil.erreur("Écran introuvable", "Impossible de charger la vue « " + vue + " » : " + e.getMessage());
        }
    }

    private static void changerScene(String vue, double largeur, double hauteur, String titre) {
        try {
            // Jamais plus grand que l'écran (ex. portable 1366 x 768).
            javafx.geometry.Rectangle2D ecran = javafx.stage.Screen.getPrimary().getVisualBounds();
            Scene scene = new Scene(chargerFxml(vue), Math.min(largeur, ecran.getWidth() * 0.96),
                    Math.min(hauteur, ecran.getHeight() * 0.94));
            URL css = SceneNavigator.class.getResource("/css/style.css");
            if (css != null) {
                scene.getStylesheets().add(css.toExternalForm());
            }
            Theme.appliquer(scene);
            stage.setScene(scene);
            stage.setTitle(titre);
            stage.centerOnScreen();
        } catch (IOException e) {
            LOG.log(java.util.logging.Level.SEVERE, "Erreur de navigation", e);
            AlertUtil.erreur("Écran introuvable", "Impossible de charger la vue « " + vue + " » : " + e.getMessage());
        }
    }

    
    /**
     * Affiche la fiche détaillée d'un mémoire.
     */
    public static void afficherDetailMemoire(Memoire memoire) {
        if (zoneContenu == null) {
            AlertUtil.erreur("Navigation",
                    "Le cadre principal n'est pas affiché.");
            return;
        }

        try {
            URL fxml = SceneNavigator.class.getResource(
                    "/view/detail_memoire.fxml");

            if (fxml == null) {
                throw new IOException(
                        "Fichier detail_memoire.fxml introuvable.");
            }

            FXMLLoader loader = new FXMLLoader(fxml);
            Parent vue = loader.load();

            DetailMemoireController controller = loader.getController();
            controller.afficherMemoire(memoire);

            zoneContenu.getChildren().setAll(vue);
            prevenir("detail_memoire");

        } catch (IOException e) {
            LOG.log(java.util.logging.Level.SEVERE, "Erreur de navigation", e);
            AlertUtil.erreur("Erreur de navigation",
                    "Impossible d'afficher le détail du mémoire : "
                            + e.getMessage());
        }
    }


    private static Parent chargerFxml(String vue) throws IOException {
        URL fxml = SceneNavigator.class.getResource("/view/" + vue + ".fxml");
        if (fxml == null) {
            throw new IOException("Fichier /view/" + vue + ".fxml introuvable");
        }
        return FXMLLoader.load(fxml);
    }
}

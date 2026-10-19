package com.tdsi.memotheque.util;

import java.io.IOException;
import java.net.URL;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * [M1] Navigation entre les écrans.
 * - afficherLogin() / afficherCadrePrincipal() : changent la scène de la fenêtre.
 * - charger("recherche") : remplace le contenu central du cadre principal par /view/recherche.fxml.
 */
public final class SceneNavigator {

    private static Stage stage;
    private static StackPane zoneContenu;

    private SceneNavigator() { }

    public static void init(Stage s) { stage = s; }

    public static Stage getStage() { return stage; }

    public static void afficherLogin() {
        zoneContenu = null;
        changerScene("login", 480, 420, "MémoTDSI - Connexion");
    }
    
    public static void afficherInscription() {
        zoneContenu = null;
        changerScene("inscription", 480, 560, "MémoTDSI - Inscription");
    }

    public static void afficherCadrePrincipal() {
        changerScene("main", 1100, 700, "MémoTDSI");
    }

    /** Appelé par MainController pour indiquer où afficher les écrans. */
    public static void definirZoneContenu(StackPane zone) { zoneContenu = zone; }

    /** Charge un écran (nom du fichier FXML sans extension) dans le cadre principal. */
    public static void charger(String vue) {
        if (zoneContenu == null) {
            AlertUtil.erreur("Navigation", "Le cadre principal n'est pas affiché.");
            return;
        }
        try {
            zoneContenu.getChildren().setAll(chargerFxml(vue));
        } catch (IOException e) {
            e.printStackTrace();
            AlertUtil.erreur("Écran introuvable", "Impossible de charger la vue « " + vue + " » : " + e.getMessage());
        }
    }

    private static void changerScene(String vue, double largeur, double hauteur, String titre) {
        try {
            Scene scene = new Scene(chargerFxml(vue), largeur, hauteur);
            URL css = SceneNavigator.class.getResource("/css/style.css");
            if (css != null) {
                scene.getStylesheets().add(css.toExternalForm());
            }
            stage.setScene(scene);
            stage.setTitle(titre);
            stage.centerOnScreen();
        } catch (IOException e) {
            e.printStackTrace();
            AlertUtil.erreur("Écran introuvable", "Impossible de charger la vue « " + vue + " » : " + e.getMessage());
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

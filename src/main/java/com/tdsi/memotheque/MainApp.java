package com.tdsi.memotheque;

import com.tdsi.memotheque.util.AlertUtil;
import com.tdsi.memotheque.util.DatabaseConnection;
import com.tdsi.memotheque.util.SceneNavigator;

import javafx.application.Application;
import javafx.stage.Stage;

/** [M1] Point d'entrée JavaFX : initialise la base puis affiche l'écran de connexion. */
public class MainApp extends Application {

    @Override
    public void start(Stage stage) {
        try {
            DatabaseConnection.initialiser();
            try {
                // [M2] Comptes par défaut (mots de passe lus dans les variables MEMOTDSI_*_PASSWORD).
                new com.tdsi.memotheque.service.AuthService().initialiserAdminParDefaut();
            } catch (IllegalStateException e) {
                AlertUtil.avertissement("Comptes par défaut", e.getMessage());
            }
            SceneNavigator.init(stage);
            SceneNavigator.afficherLogin();
            stage.show();
        } catch (Exception e) {
            java.util.logging.Logger.getLogger(MainApp.class.getName())
                    .log(java.util.logging.Level.SEVERE, "Démarrage impossible", e);
            AlertUtil.erreur("Démarrage impossible", e.getMessage());
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}

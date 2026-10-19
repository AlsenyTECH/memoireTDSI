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
            SceneNavigator.init(stage);
            SceneNavigator.afficherLogin();
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            AlertUtil.erreur("Démarrage impossible", e.getMessage());
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}

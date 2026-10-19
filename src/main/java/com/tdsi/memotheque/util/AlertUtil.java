package com.tdsi.memotheque.util;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;

/** [M1] Boîtes de dialogue standard. */
public final class AlertUtil {

    private AlertUtil() { }

    public static void info(String titre, String message) {
        afficher(AlertType.INFORMATION, titre, message);
    }

    public static void erreur(String titre, String message) {
        afficher(AlertType.ERROR, titre, message);
    }

    public static void avertissement(String titre, String message) {
        afficher(AlertType.WARNING, titre, message);
    }

    /** Demande une confirmation : retourne true si l'utilisateur clique sur OK. */
    public static boolean confirmer(String titre, String message) {
        Alert a = new Alert(AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        a.setTitle(titre);
        a.setHeaderText(null);
        return a.showAndWait().filter(b -> b == ButtonType.OK).isPresent();
    }

    private static void afficher(AlertType type, String titre, String message) {
        Alert a = new Alert(type);
        a.setTitle(titre);
        a.setHeaderText(null);
        a.setContentText(message);
        a.showAndWait();
    }
}

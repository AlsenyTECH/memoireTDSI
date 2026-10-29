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

    /** Demande un mot de passe (champ masqué). Vide si l'utilisateur annule. */
    public static java.util.Optional<String> demanderMotDePasse(String titre, String message) {
        javafx.scene.control.Dialog<String> d = new javafx.scene.control.Dialog<>();
        d.setTitle(titre);
        d.setHeaderText(message);
        javafx.scene.control.PasswordField champ = new javafx.scene.control.PasswordField();
        champ.setPromptText("Mot de passe");
        d.getDialogPane().setContent(champ);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        d.setResultConverter(b -> b == ButtonType.OK ? champ.getText() : null);
        javafx.application.Platform.runLater(champ::requestFocus);
        return d.showAndWait().filter(s -> !s.isEmpty());
    }

    /** Petit tableau en lecture seule dans une boîte de dialogue (historique, détails...). */
    public static void afficherTableau(String titre, String[] colonnes, java.util.List<String[]> lignes) {
        javafx.scene.control.TableView<String[]> table = new javafx.scene.control.TableView<>();
        for (int i = 0; i < colonnes.length; i++) {
            final int index = i;
            javafx.scene.control.TableColumn<String[], String> col = new javafx.scene.control.TableColumn<>(colonnes[i]);
            col.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue()[index]));
            col.setPrefWidth(130);
            table.getColumns().add(col);
        }
        table.getItems().setAll(lignes);
        table.setPrefSize(820, 260);

        Alert a = new Alert(AlertType.INFORMATION);
        a.setTitle(titre);
        a.setHeaderText(titre);
        a.getDialogPane().setContent(table);
        a.setResizable(true);
        a.showAndWait();
    }

    private static void afficher(AlertType type, String titre, String message) {
        Alert a = new Alert(type);
        a.setTitle(titre);
        a.setHeaderText(null);
        a.setContentText(message);
        a.showAndWait();
    }
}

package com.tdsi.memotheque.controller;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;

import com.tdsi.memotheque.model.Notification;
import com.tdsi.memotheque.service.NotificationService;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;

/** Notifications de l'utilisateur connecté (non lues en gras, en tête de liste). */
public class NotificationController {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML private ListView<Notification> listeNotifications;
    @FXML private Label labelMessage;

    private final NotificationService notificationService = new NotificationService();

    @FXML
    private void initialize() {
        listeNotifications.setCellFactory(l -> new ListCell<>() {
            @Override
            protected void updateItem(Notification n, boolean vide) {
                super.updateItem(n, vide);
                getStyleClass().remove("notification-non-lue");
                if (vide || n == null) {
                    setText(null);
                    return;
                }
                setText((n.isLue() ? "" : "● ") + n.getDateHeure().format(FORMAT) + "  —  " + n.getMessage());
                setWrapText(true);
                setPrefWidth(0);
                if (!n.isLue()) {
                    getStyleClass().add("notification-non-lue");
                }
            }
        });
        charger();
    }

    private void charger() {
        try {
            listeNotifications.getItems().setAll(notificationService.mesNotifications());
        } catch (SQLException | SecurityException e) {
            labelMessage.setText("Notifications indisponibles : " + e.getMessage());
        }
    }

    @FXML
    private void toutMarquerLu() {
        try {
            notificationService.marquerToutesLues();
            charger();
            MainController.rafraichirCompteur();
        } catch (SQLException | SecurityException e) {
            labelMessage.setText(e.getMessage());
        }
    }

    @FXML
    private void supprimerLues() {
        try {
            notificationService.supprimerLues();
            charger();
        } catch (SQLException | SecurityException e) {
            labelMessage.setText(e.getMessage());
        }
    }
}

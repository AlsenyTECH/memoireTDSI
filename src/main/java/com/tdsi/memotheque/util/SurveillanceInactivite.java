package com.tdsi.memotheque.util;

import java.time.Duration;
import java.time.Instant;

import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.service.JournalService;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.input.InputEvent;

/**
 * Déconnexion automatique après 15 minutes sans clic ni frappe clavier
 * (poste partagé en salle machine, bibliothèque...).
 */
public final class SurveillanceInactivite {

    public static final Duration DELAI = Duration.ofMinutes(15);

    private static Timeline minuterie;

    private SurveillanceInactivite() { }

    /** À appeler quand le cadre principal est affiché. */
    public static void demarrer(Scene scene) {
        arreter();
        scene.addEventFilter(InputEvent.ANY, e -> SessionManager.toucher());
        SessionManager.toucher();

        minuterie = new Timeline(new KeyFrame(javafx.util.Duration.seconds(20), e -> verifier()));
        minuterie.setCycleCount(Animation.INDEFINITE);
        minuterie.play();
    }

    public static void arreter() {
        if (minuterie != null) {
            minuterie.stop();
            minuterie = null;
        }
    }

    private static void verifier() {
        if (!SessionManager.estInactiveDepuis(DELAI, Instant.now())) {
            return;
        }
        Utilisateur u = SessionManager.getUtilisateur();
        arreter();
        new JournalService().log(u.getId(), "DECONNEXION_INACTIVITE",
                "Session fermée après " + DELAI.toMinutes() + " minutes sans activité");
        SessionManager.fermer();

        // Hors du traitement de l'animation : un dialogue modal y est interdit.
        Platform.runLater(() -> {
            SceneNavigator.afficherLogin();
            AlertUtil.info("Session expirée",
                    "Vous avez été déconnecté après " + DELAI.toMinutes() + " minutes d'inactivité.");
        });
    }
}

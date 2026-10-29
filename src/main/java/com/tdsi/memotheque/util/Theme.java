package com.tdsi.memotheque.util;

import java.net.URL;
import java.util.prefs.Preferences;

import javafx.scene.Scene;

/** Thème clair / sombre, mémorisé pour l'utilisateur du système (java.util.prefs). */
public final class Theme {

    private static final String CLE = "theme-sombre";
    private static final String CSS_SOMBRE = "/css/theme-sombre.css";

    private Theme() { }

    public static boolean estSombre() {
        try {
            return Preferences.userNodeForPackage(Theme.class).getBoolean(CLE, false);
        } catch (SecurityException e) {
            return false;
        }
    }

    public static void choisirSombre(boolean sombre, Scene scene) {
        try {
            Preferences.userNodeForPackage(Theme.class).putBoolean(CLE, sombre);
        } catch (SecurityException e) {
            // préférences indisponibles : le choix vaut pour la session seulement
        }
        appliquer(scene, sombre);
    }

    /** Ajoute ou retire la feuille sombre (appelé à chaque changement de scène). */
    public static void appliquer(Scene scene) {
        appliquer(scene, estSombre());
    }

    private static void appliquer(Scene scene, boolean sombre) {
        URL css = Theme.class.getResource(CSS_SOMBRE);
        if (scene == null || css == null) {
            return;
        }
        String url = css.toExternalForm();
        scene.getStylesheets().remove(url);
        if (sombre) {
            scene.getStylesheets().add(url);
        }
    }
}

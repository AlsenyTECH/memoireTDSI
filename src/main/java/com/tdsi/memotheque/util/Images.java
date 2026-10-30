package com.tdsi.memotheque.util;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import javafx.scene.image.Image;

/**
 * Images de l'application (illustrations du campus, logo).
 * Une vraie photo du campus peut remplacer l'illustration : il suffit de déposer
 * campus.jpg (ou campus.png) dans le dossier « images » du dossier de données.
 */
public final class Images {

    private Images() { }

    /** Logo carré (32, 64, 128 ou 256 pixels), ou null s'il manque. */
    public static Image logo(int taille) {
        try (InputStream in = Images.class.getResourceAsStream("/images/logo-" + taille + ".png")) {
            return in == null ? null : new Image(in);
        } catch (java.io.IOException e) {
            return null;
        }
    }

    /** URL d'une photo du campus fournie par l'établissement, ou null pour garder l'illustration. */
    public static String photoCampus() {
        for (String nom : new String[] {"campus.jpg", "campus.jpeg", "campus.png"}) {
            Path p = Donnees.chemin("images").resolve(nom);
            if (Files.isRegularFile(p)) {
                return p.toUri().toString();
            }
        }
        return null;
    }
}

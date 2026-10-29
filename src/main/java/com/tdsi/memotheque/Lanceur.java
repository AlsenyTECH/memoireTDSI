package com.tdsi.memotheque;

import java.nio.file.Path;

import com.tdsi.memotheque.util.Donnees;

/**
 * Point d'entrée de l'application installée (jar ou paquet .deb).
 *
 * Une classe qui n'hérite pas d'Application est nécessaire pour lancer JavaFX depuis le classpath.
 * Installée (jpackage), l'application range ses données dans ~/.local/share/memotdsi
 * au lieu du dossier courant ; « -Dmemotdsi.donnees=... » permet de choisir un autre dossier.
 */
public final class Lanceur {

    private Lanceur() { }

    public static void main(String[] args) {
        boolean installee = System.getProperty("jpackage.app-version") != null;
        if (System.getProperty(Donnees.PROPRIETE) == null && installee) {
            String xdg = System.getenv("XDG_DATA_HOME");
            Path base = xdg == null || xdg.isBlank()
                    ? Path.of(System.getProperty("user.home"), ".local", "share")
                    : Path.of(xdg);
            System.setProperty(Donnees.PROPRIETE, base.resolve("memotdsi").toString());
        }
        MainApp.main(args);
    }
}

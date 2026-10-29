package com.tdsi.memotheque.util;

import java.nio.file.Path;

/**
 * Dossier des données de l'application : base, storage/, coffre de clés.
 *
 * Par défaut le dossier courant (lancement avec « mvn javafx:run » depuis le projet).
 * Propriété système « memotdsi.donnees » pour le changer : l'application installée
 * (paquet .deb) utilise ~/.local/share/memotdsi (voir Lanceur).
 */
public final class Donnees {

    public static final String PROPRIETE = "memotdsi.donnees";

    private Donnees() { }

    public static Path dossier() {
        String valeur = System.getProperty(PROPRIETE);
        return valeur == null || valeur.isBlank() ? Path.of("") : Path.of(valeur);
    }

    public static Path chemin(String nom) {
        return dossier().resolve(nom);
    }
}

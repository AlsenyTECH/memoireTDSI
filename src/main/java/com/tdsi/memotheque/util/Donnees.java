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

    /**
     * Crée le dossier de données. Quand il est configuré (application installée),
     * il est réservé à son propriétaire (droits 700) : base, PDF et coffre ne sont pas
     * lisibles par les autres comptes du poste.
     */
    public static void preparer() throws java.io.IOException {
        Path d = dossier().toAbsolutePath();
        java.nio.file.Files.createDirectories(d);
        if (System.getProperty(PROPRIETE) != null) {
            try {
                java.nio.file.Files.setPosixFilePermissions(d,
                        java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
            } catch (UnsupportedOperationException e) {
                // Windows : droits du profil utilisateur
            }
        }
    }
}

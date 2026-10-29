package com.tdsi.memotheque.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * [M1] Connexion SQLite + création du schéma au premier lancement.
 * La base est le fichier « memotdsi.db » (ignoré par Git).
 */
public final class DatabaseConnection {

   private static final String URL_PAR_DEFAUT = "jdbc:sqlite:memotdsi.db";
   private static String url = URL_PAR_DEFAUT;

    private DatabaseConnection() { }

    /** Ouvre une connexion (à fermer avec try-with-resources) avec les clés étrangères activées. */
    public static Connection getConnection() throws SQLException {
        Connection c = DriverManager.getConnection(url);
        try (Statement st = c.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
        }
        return c;
    }

    /**
     * Permet aux tests de choisir une base SQLite différente.
     */
    public static void utiliserBasePourTests(String urlTest) {
        if (urlTest == null || !urlTest.startsWith("jdbc:sqlite:")) {
            throw new IllegalArgumentException(
                    "URL SQLite invalide pour les tests");
        }

        url = urlTest;
    }

    /** Rétablit la base habituelle de l'application. */
    public static void utiliserBaseParDefaut() {
        url = URL_PAR_DEFAUT;
    }

    /** Crée les tables (schema.sql), insère les données de départ (seed.sql), puis applique les migrations. */
    public static void initialiser() throws SQLException, IOException {
        executerScript("/db/schema.sql");
        executerScript("/db/seed.sql");
        Migrations.appliquer();
    }

    private static void executerScript(String ressource) throws SQLException, IOException {
        String contenu;
        try (InputStream in = DatabaseConnection.class.getResourceAsStream(ressource)) {
            if (in == null) {
                throw new IOException("Script SQL introuvable : " + ressource);
            }
            contenu = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        // On retire les lignes de commentaires entières, puis on découpe sur ';'
        StringBuilder sansCommentaires = new StringBuilder();
        for (String ligne : contenu.split("\\R")) {
            if (!ligne.trim().startsWith("--")) {
                sansCommentaires.append(ligne).append('\n');
            }
        }

        try (Connection c = getConnection(); Statement st = c.createStatement()) {
            for (String instruction : sansCommentaires.toString().split(";")) {
                if (!instruction.isBlank()) {
                    st.execute(instruction.trim());
                }
            }
        }
    }
}

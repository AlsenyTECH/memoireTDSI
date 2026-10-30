package com.tdsi.memotheque.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.logging.Logger;

/**
 * Évolutions du schéma SQL après schema.sql.
 * Chaque script de /db/migrations est appliqué UNE seule fois, dans une transaction,
 * puis noté dans la table schema_version. Une base existante est donc mise à jour
 * au démarrage sans perdre ses données.
 *
 * Pour ajouter une évolution : créer V{n}__nom.sql et l'ajouter à la fin de SCRIPTS.
 */
public final class Migrations {

    private static final Logger LOG = Logger.getLogger(Migrations.class.getName());

    /** Ordre d'application. Ne jamais modifier ni renuméroter un script déjà publié. */
    private static final String[] SCRIPTS = {
        "V2__securite_comptes.sql",
        "V3__journal_chaine.sql",
        "V4__fichiers_versions.sql",
        "V5__signatures.sql",
        "V6__recherche_plein_texte.sql",
        "V7__notifications.sql",
        "V8__circuit_universitaire.sql",
    };

    private Migrations() { }

    public static void appliquer() throws SQLException, IOException {
        try (Connection c = DatabaseConnection.getConnection()) {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS schema_version (
                            version INTEGER PRIMARY KEY,
                            script  TEXT NOT NULL,
                            date_application TEXT NOT NULL
                        )""");
            }

            for (String script : SCRIPTS) {
                int version = numero(script);
                if (dejaAppliquee(c, version)) {
                    continue;
                }
                appliquerScript(c, version, script);
                LOG.info(() -> "Migration appliquée : " + script);
            }
        }
    }

    /** Version la plus récente appliquée (utile pour les tests et le diagnostic). */
    public static int versionCourante() throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(version), 1) FROM schema_version")) {
            return rs.next() ? rs.getInt(1) : 1;
        }
    }

    private static boolean dejaAppliquee(Connection c, int version) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM schema_version WHERE version = ?")) {
            ps.setInt(1, version);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void appliquerScript(Connection c, int version, String script)
            throws SQLException, IOException {
        String sql = lire("/db/migrations/" + script);

        // Reconstruction de table : les clés étrangères doivent être coupées AVANT la transaction
        // (sinon DROP TABLE déclencherait les ON DELETE CASCADE). Procédure recommandée par SQLite.
        boolean reconstruction = sql.startsWith("-- @reconstruction");
        if (reconstruction) {
            try (Statement st = c.createStatement()) {
                st.execute("PRAGMA foreign_keys = OFF");
            }
        }

        boolean autoCommit = c.getAutoCommit();
        c.setAutoCommit(false);
        try (Statement st = c.createStatement();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO schema_version (version, script, date_application) VALUES (?, ?, ?)")) {

            // sqlite-jdbc exécute toutes les instructions du texte (triggers compris).
            st.executeUpdate(sql);

            ps.setInt(1, version);
            ps.setString(2, script);
            ps.setString(3, LocalDateTime.now().withNano(0).toString());
            ps.executeUpdate();

            if (reconstruction) {
                try (ResultSet rs = st.executeQuery("PRAGMA foreign_key_check")) {
                    if (rs.next()) {
                        throw new SQLException("Clé étrangère invalide après reconstruction (table "
                                + rs.getString(1) + ")");
                    }
                }
            }
            c.commit();
        } catch (SQLException e) {
            c.rollback();
            throw new SQLException("Échec de la migration " + script + " : " + e.getMessage(), e);
        } finally {
            c.setAutoCommit(autoCommit);
            if (reconstruction) {
                try (Statement st = c.createStatement()) {
                    st.execute("PRAGMA foreign_keys = ON");
                }
            }
        }
    }

    private static int numero(String script) {
        return Integer.parseInt(script.substring(1, script.indexOf("__")));
    }

    private static String lire(String ressource) throws IOException {
        try (InputStream in = Migrations.class.getResourceAsStream(ressource)) {
            if (in == null) {
                throw new IOException("Script de migration introuvable : " + ressource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

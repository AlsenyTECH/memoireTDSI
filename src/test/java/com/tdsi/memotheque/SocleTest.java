package com.tdsi.memotheque;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tdsi.memotheque.util.DatabaseConnection;
import com.tdsi.memotheque.util.GestionCles;
import com.tdsi.memotheque.util.Migrations;

/** Migrations du schéma et coffre de clés. */
class SocleTest {

    @TempDir
    Path dossier;

    @AfterEach
    void nettoyer() {
        BaseDeTest.fermer();
    }

    @Test
    void baseNeuveAToutesLesMigrations() throws Exception {
        BaseDeTest.ouvrirBaseTemporaire(dossier);

        assertEquals(7, Migrations.versionCourante());
        assertTrue(tableExiste("memoire_fts"));
        assertTrue(tableExiste("cle_signature"));
        assertTrue(tableExiste("notification"));
        assertTrue(tableExiste("memoire_version"));
    }

    @Test
    void relancerLInitialisationNeRejouePasLesMigrations() throws Exception {
        BaseDeTest.ouvrirBaseTemporaire(dossier);
        DatabaseConnection.initialiser();
        DatabaseConnection.initialiser();

        try (Connection c = DatabaseConnection.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM schema_version")) {
            assertEquals(6, rs.getInt(1));
        }
    }

    @Test
    void uneAncienneBaseEstMiseAJourSansPerteDeDonnees() throws Exception {
        // Base « version 1 » : seulement schema.sql + seed.sql, avec un compte.
        DatabaseConnection.utiliserBasePourTests("jdbc:sqlite:" + dossier.resolve("ancienne.db"));
        GestionCles.utiliserPourTests(dossier);
        try (Connection c = DatabaseConnection.getConnection(); Statement st = c.createStatement()) {
            String schema = new String(SocleTest.class.getResourceAsStream("/db/schema.sql").readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            for (String instr : schema.replaceAll("(?m)^\\s*--.*$", "").split(";")) {
                if (!instr.isBlank()) {
                    st.execute(instr);
                }
            }
            st.executeUpdate("INSERT INTO utilisateur (nom, prenom, email, mot_de_passe_hash, sel, role, "
                    + "actif, date_creation) VALUES ('Ba', 'Awa', 'awa@test.sn', 'h', 's', 'ADMIN', 1, '2025-01-01')");
        }

        DatabaseConnection.initialiser();

        assertEquals(7, Migrations.versionCourante());
        try (Connection c = DatabaseConnection.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT email, doit_changer_mdp FROM utilisateur")) {
            assertTrue(rs.next());
            assertEquals("awa@test.sn", rs.getString(1));
            assertEquals(0, rs.getInt(2));
        }
    }

    @Test
    void lesClesSontCreeesUneFoisPuisRelues() throws Exception {
        GestionCles.utiliserPourTests(dossier);
        SecretKey hmac = GestionCles.cleHmacJournal();
        SecretKey aes = GestionCles.cleChiffrementFichiers();

        assertEquals(32, hmac.getEncoded().length);
        assertEquals(32, aes.getEncoded().length);
        assertEquals("AES", aes.getAlgorithm());
        assertTrue(Files.exists(dossier.resolve("memotdsi-test.p12")));

        // Rechargement depuis le fichier : mêmes clés.
        GestionCles.utiliserPourTests(dossier);
        assertArrayEquals(hmac.getEncoded(), GestionCles.cleHmacJournal().getEncoded());
        assertArrayEquals(aes.getEncoded(), GestionCles.cleChiffrementFichiers().getEncoded());
        assertFalse(java.util.Arrays.equals(hmac.getEncoded(), aes.getEncoded()));
    }

    private boolean tableExiste(String nom) throws Exception {
        try (Connection c = DatabaseConnection.getConnection();
             var ps = c.prepareStatement("SELECT 1 FROM sqlite_master WHERE name = ?")) {
            ps.setString(1, nom);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}

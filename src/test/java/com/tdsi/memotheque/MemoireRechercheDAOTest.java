
package com.tdsi.memotheque;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tdsi.memotheque.dao.impl.MemoireRechercheDAOImpl;
import com.tdsi.memotheque.model.Mention;
import com.tdsi.memotheque.model.Memoire;
import com.tdsi.memotheque.model.MemoireCritere;
import com.tdsi.memotheque.model.Niveau;
import com.tdsi.memotheque.model.StatutMemoire;
import com.tdsi.memotheque.util.DatabaseConnection;

class MemoireRechercheDAOTest {

    @TempDir
    Path dossierTemporaire;

    private MemoireRechercheDAOImpl dao;
    private int idDomaineCyber;
    private int idDomaineReseaux;

    @BeforeEach
    void preparerBase() throws Exception {
        Path fichier = dossierTemporaire.resolve("test.db");

        DatabaseConnection.utiliserBasePourTests(
                "jdbc:sqlite:" + fichier.toAbsolutePath());

        DatabaseConnection.initialiser();

        dao = new MemoireRechercheDAOImpl();

        idDomaineCyber = trouverIdDomaine("Cybersécurité");
        idDomaineReseaux =
                trouverIdDomaine("Réseaux et télécommunications");

        int auteur1 = insererUtilisateur(
                "test1@example.com", "Diallo", "Aminata");

        int auteur2 = insererUtilisateur(
                "test2@example.com", "Fall", "Moussa");

        int auteur3 = insererUtilisateur(
                "test3@example.com", "Sow", "Fatou");

        int auteur4 = insererUtilisateur(
                "test4@example.com", "Ba", "Ibrahima");

        insererMemoire(
                "Sécurité des applications web",
                "Étude des vulnérabilités web",
                "cybersécurité, web, OWASP",
                "2024-2025", "M1",
                idDomaineCyber, auteur1, "VALIDE",
                "2025-05-10");

        insererMemoire(
                "Administration des réseaux",
                "Configuration et supervision réseau",
                "réseaux, supervision, TCP/IP",
                "2023-2024", "L3",
                idDomaineReseaux, auteur2, "VALIDE",
                "2024-06-15");

        insererMemoire(
                "Cryptographie appliquée",
                "Protection des données sensibles",
                "cryptographie, sécurité",
                "2025-2026", "M2",
                idDomaineCyber, auteur3, "VALIDE",
                "2026-03-20");

        insererMemoire(
                "Sécurité en cours de validation",
                "Document non encore publié",
                "sécurité, test",
                "2024-2025", "L2",
                idDomaineCyber, auteur4, "EN_ATTENTE",
                null);
    }

    @AfterEach
    void restaurerBaseParDefaut() {
        DatabaseConnection.utiliserBaseParDefaut();
    }

    @Test
    void rechercheParMotCle() throws SQLException {
        MemoireCritere critere = new MemoireCritere();
        critere.setMotCle("OWASP");

        List<Memoire> resultats = dao.rechercher(critere);

        assertEquals(1, resultats.size());
        assertEquals("Sécurité des applications web",
                resultats.get(0).getTitre());
    }

    @Test
    void rechercheAvecFiltresCombines() throws SQLException {
        MemoireCritere critere = new MemoireCritere();
        critere.setIdDomaine(idDomaineCyber);
        critere.setNiveau(Niveau.M1);

        List<Memoire> resultats = dao.rechercher(critere);

        assertEquals(1, resultats.size());
        assertEquals("Sécurité des applications web",
                resultats.get(0).getTitre());
    }

    @Test
    void paginationRetourneDesPagesDistinctes()
            throws SQLException {
        MemoireCritere critere = new MemoireCritere();
        critere.setTaille(1);
        critere.setPage(1);

        List<Memoire> page1 = dao.rechercher(critere);

        critere.setPage(2);
        List<Memoire> page2 = dao.rechercher(critere);

        assertEquals(1, page1.size());
        assertEquals(1, page2.size());
        assertNotEquals(
                page1.get(0).getId(),
                page2.get(0).getId());

        assertEquals(3, dao.compter(new MemoireCritere()));
    }

    @Test
    void rechercheSansResultat() throws SQLException {
        MemoireCritere critere = new MemoireCritere();
        critere.setMotCle("texte_inexistant_987654");

        List<Memoire> resultats = dao.rechercher(critere);

        assertTrue(resultats.isEmpty());
        assertEquals(0, dao.compter(critere));
    }

    @Test
    void filtreParAnneeAcademique() throws SQLException {
        MemoireCritere critere = new MemoireCritere();
        critere.setAnneeAcademique("2023-2024");

        List<Memoire> resultats = dao.rechercher(critere);

        assertEquals(1, resultats.size());
        assertEquals("Administration des réseaux",
                resultats.get(0).getTitre());
    }

    @Test
    void filtreParMention() throws SQLException {
        MemoireCritere critere = new MemoireCritere();
        critere.setMention(Mention.BIEN);

        List<Memoire> resultats = dao.rechercher(critere);

        assertEquals(1, resultats.size());
        assertEquals("Cryptographie appliquée",
                resultats.get(0).getTitre());
    }

    @Test
    void filtreParDateDePublication() throws SQLException {
        MemoireCritere critere = new MemoireCritere();
        critere.setDateDebut(LocalDate.parse("2025-01-01"));
        critere.setDateFin(LocalDate.parse("2025-12-31"));

        List<Memoire> resultats = dao.rechercher(critere);

        assertEquals(1, resultats.size());
        assertEquals("Sécurité des applications web",
                resultats.get(0).getTitre());
    }

    @Test
    void lesMemoiresNonValidesSontExclusParDefaut()
            throws SQLException {
        MemoireCritere critere = new MemoireCritere();

        List<Memoire> resultats = dao.rechercher(critere);

        assertEquals(3, resultats.size());

        for (Memoire memoire : resultats) {
            assertEquals(
                    StatutMemoire.VALIDE,
                    memoire.getStatut());
        }
    }

    @Test
    void incrementerVuesEtTelechargements() throws SQLException {
        MemoireCritere critere = new MemoireCritere();
        critere.setMotCle("OWASP");

        Memoire memoire = dao.rechercher(critere).get(0);
        int id = memoire.getId();

        dao.incrementerVues(id);
        dao.incrementerTelechargements(id);

        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT nb_vues, nb_telechargements "
                             + "FROM memoire WHERE id = ?")) {

            ps.setInt(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt("nb_vues"));
                assertEquals(1,
                        rs.getInt("nb_telechargements"));
            }
        }
    }

    private int trouverIdDomaine(String nom)
            throws SQLException {
        String sql = "SELECT id FROM domaine WHERE nom = ?";

        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, nom);

            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Domaine absent : " + nom);
                return rs.getInt("id");
            }
        }
    }

    private int insererUtilisateur(
            String email, String nom, String prenom)
            throws SQLException {

        String sql =
                "INSERT INTO utilisateur "
                + "(nom, prenom, email, mot_de_passe_hash, sel, "
                + "role, niveau, actif, verrouille, date_creation) "
                + "VALUES (?, ?, ?, 'hash_test', 'sel_test', "
                + "'ETUDIANT', 'M1', 1, 0, '2026-01-01')";

        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, nom);
            ps.setString(2, prenom);
            ps.setString(3, email);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }

        throw new SQLException(
                "Impossible de créer l'utilisateur");
    }

    private void insererMemoire(
            String titre,
            String resume,
            String motsCles,
            String annee,
            String niveau,
            int idDomaine,
            int idAuteur,
            String statut,
            String datePublication) throws SQLException {

        String sql =
                "INSERT INTO memoire ("
                + "titre, resume, mots_cles, annee_academique, niveau, "
                + "id_domaine, id_auteur, date_depot, date_publication, "
                + "chemin_fichier, nom_original, taille_octets, "
                + "empreinte_sha256, statut, mention) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, titre);
            ps.setString(2, resume);
            ps.setString(3, motsCles);
            ps.setString(4, annee);
            ps.setString(5, niveau);
            ps.setInt(6, idDomaine);
            ps.setInt(7, idAuteur);
            ps.setString(8, "2026-01-01");
            ps.setString(9, datePublication);
            ps.setString(10, "/test/" + titre + ".pdf");
            ps.setString(11, titre + ".pdf");
            ps.setLong(12, 1024);
            ps.setString(13, "empreinte_test_" + idAuteur);
            ps.setString(14, statut);

            if ("Cryptographie appliquée".equals(titre)) {
                ps.setString(15, "BIEN");
            } else {
                ps.setNull(15, java.sql.Types.VARCHAR);
            }

            ps.executeUpdate();
        }
    }
}

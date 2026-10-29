package com.tdsi.memotheque.dao.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import javax.crypto.SecretKey;

import com.tdsi.memotheque.dao.JournalDAO;
import com.tdsi.memotheque.model.JournalActivite;
import com.tdsi.memotheque.model.VerificationJournal;
import com.tdsi.memotheque.util.CryptoUtil;
import com.tdsi.memotheque.util.DatabaseConnection;
import com.tdsi.memotheque.util.GestionCles;

/**
 * [M6] Implémentation JDBC de JournalDAO.
 *
 * Journal infalsifiable : hmac(n) = HMAC-SHA256(K, hmac(n-1) || id_utilisateur || action || détails || date).
 * K est dans le coffre de clés (hors base) : modifier, insérer ou supprimer une ligne
 * casse la chaîne. Une « ancre » (nombre de lignes + dernier hmac, elle-même authentifiée par K)
 * est écrite à côté du coffre pour détecter aussi la suppression des DERNIÈRES lignes.
 */
public class JournalDAOImpl implements JournalDAO {

    private static final Logger LOG = Logger.getLogger(JournalDAOImpl.class.getName());
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String ORIGINE = "MemoTDSI-journal-v1";

    @Override
    public void enregistrer(JournalActivite j) throws SQLException {
        if (j.getDateHeure() == null) {
            j.setDateHeure(LocalDateTime.now());
        }
        String date = j.getDateHeure().format(FORMAT);
        SecretKey cle = GestionCles.cleHmacJournal();

        String hmac;
        long nombre;

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String precedent = dernierHmac(conn);
                hmac = calculerHmac(cle, precedent, j.getIdUtilisateur(), j.getAction(), j.getDetails(), date);

                try (PreparedStatement ps = conn.prepareStatement("""
                        INSERT INTO journal_activite
                        (id_utilisateur, action, details, date_heure, hmac)
                        VALUES (?, ?, ?, ?, ?)
                        """)) {
                    if (j.getIdUtilisateur() == null) {
                        ps.setNull(1, java.sql.Types.INTEGER);
                    } else {
                        ps.setInt(1, j.getIdUtilisateur());
                    }
                    ps.setString(2, j.getAction());
                    ps.setString(3, j.getDetails());
                    ps.setString(4, date);
                    ps.setString(5, hmac);
                    ps.executeUpdate();
                }
                nombre = compterLignesProtegees(conn);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }

        j.setHmac(hmac);
        ecrireAncre(cle, nombre, hmac);
    }

    @Override
    public List<JournalActivite> derniers(int limite) throws SQLException {
        if (limite < 1) {
            throw new IllegalArgumentException(
                "La limite doit être supérieure à zéro."
            );
        }

        String sql = """
            SELECT id, id_utilisateur, action, details, date_heure, hmac
            FROM journal_activite
            ORDER BY date_heure DESC, id DESC
            LIMIT ?
            """;

        List<JournalActivite> journal = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, Math.min(limite, 200));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    journal.add(lire(rs));
                }
            }
        }

        return journal;
    }

    @Override
    public VerificationJournal verifierChaine() throws SQLException {
        SecretKey cle = GestionCles.cleHmacJournal();

        String precedent = "";
        int verifiees = 0;
        int nonProtegees = 0;
        boolean chaineCommencee = false;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, id_utilisateur, action, details, date_heure, hmac FROM journal_activite ORDER BY id");
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                JournalActivite j = lire(rs);
                String date = rs.getString("date_heure");

                if (j.getHmac() == null) {
                    if (chaineCommencee) {
                        return new VerificationJournal(false, verifiees, nonProtegees, j.getId(),
                                "Ligne #" + j.getId() + " sans HMAC au milieu de la chaîne : ligne insérée à la main.");
                    }
                    nonProtegees++;
                    continue;
                }
                chaineCommencee = true;

                String attendu = calculerHmac(cle, precedent, j.getIdUtilisateur(), j.getAction(),
                        j.getDetails(), date);
                if (!CryptoUtil.egal(attendu, j.getHmac())) {
                    return new VerificationJournal(false, verifiees, nonProtegees, j.getId(),
                            "Ligne #" + j.getId() + " modifiée, ou ligne précédente supprimée.");
                }
                precedent = j.getHmac();
                verifiees++;
            }
        }

        // Fin de chaîne : l'ancre doit correspondre (sinon des dernières lignes ont été supprimées).
        String ancre = lireAncre();
        if (ancre == null) {
            return verifiees == 0
                    ? new VerificationJournal(true, 0, nonProtegees, null, "Journal vide : rien à vérifier.")
                    : new VerificationJournal(false, verifiees, nonProtegees, null,
                            "Ancre du journal absente : impossible de garantir qu'aucune ligne finale n'a été supprimée.");
        }
        if (!CryptoUtil.egal(ancre, contenuAncre(cle, verifiees, precedent))) {
            return new VerificationJournal(false, verifiees, nonProtegees, null,
                    "Le journal ne correspond pas à son ancre : des lignes récentes ont été supprimées "
                            + "(ou l'application s'est arrêtée brutalement pendant une écriture).");
        }

        return new VerificationJournal(true, verifiees, nonProtegees, null,
                "Journal intact : " + verifiees + " ligne(s) vérifiée(s)"
                        + (nonProtegees > 0 ? ", " + nonProtegees + " ligne(s) antérieure(s) non protégée(s)." : "."));
    }

    // ------------------------------------------------------------------ outils

    static String calculerHmac(SecretKey cle, String precedent, Integer idUtilisateur,
            String action, String details, String date) {
        return CryptoUtil.hmacHex(cle, CryptoUtil.encoder(ORIGINE, precedent,
                idUtilisateur == null ? null : idUtilisateur.toString(), action, details, date));
    }

    private String dernierHmac(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT hmac FROM journal_activite WHERE hmac IS NOT NULL ORDER BY id DESC LIMIT 1");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getString(1) : "";
        }
    }

    private long compterLignesProtegees(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM journal_activite WHERE hmac IS NOT NULL");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    private JournalActivite lire(ResultSet rs) throws SQLException {
        JournalActivite j = new JournalActivite();
        j.setId(rs.getInt("id"));
        int idUtilisateur = rs.getInt("id_utilisateur");
        j.setIdUtilisateur(rs.wasNull() ? null : idUtilisateur);
        j.setAction(rs.getString("action"));
        j.setDetails(rs.getString("details"));
        j.setDateHeure(LocalDateTime.parse(rs.getString("date_heure"), FORMAT));
        j.setHmac(rs.getString("hmac"));
        return j;
    }

    private static Path fichierAncre() {
        return GestionCles.fichier().toAbsolutePath().resolveSibling(
                GestionCles.fichier().getFileName() + ".journal-ancre");
    }

    private static String contenuAncre(SecretKey cle, long nombre, String dernier) {
        String mac = CryptoUtil.hmacHex(cle, CryptoUtil.encoder("MemoTDSI-ancre-v1", Long.toString(nombre), dernier));
        return nombre + ":" + dernier + ":" + mac;
    }

    private void ecrireAncre(SecretKey cle, long nombre, String dernier) {
        Path ancre = fichierAncre();
        try {
            Path tmp = Files.createTempFile(ancre.getParent(), "ancre", ".tmp");
            Files.writeString(tmp, contenuAncre(cle, nombre, dernier), StandardCharsets.US_ASCII);
            Files.move(tmp, ancre, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOG.warning(() -> "Impossible d'écrire l'ancre du journal " + ancre + " : " + e.getMessage());
        }
    }

    private String lireAncre() {
        try {
            Path ancre = fichierAncre();
            return Files.exists(ancre) ? Files.readString(ancre, StandardCharsets.US_ASCII).trim() : null;
        } catch (IOException e) {
            return null;
        }
    }
}

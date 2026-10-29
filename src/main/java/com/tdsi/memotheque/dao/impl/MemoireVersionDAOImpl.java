package com.tdsi.memotheque.dao.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.tdsi.memotheque.dao.MemoireVersionDAO;
import com.tdsi.memotheque.model.Memoire;
import com.tdsi.memotheque.model.MemoireVersion;
import com.tdsi.memotheque.model.StatutMemoire;
import com.tdsi.memotheque.util.DatabaseConnection;

/** Implémentation JDBC de l'historique des versions. */
public class MemoireVersionDAOImpl implements MemoireVersionDAO {

    @Override
    public void archiver(Memoire m) throws SQLException {
        String sql = """
                INSERT INTO memoire_version
                (id_memoire, chemin_fichier, nom_original, taille_octets, empreinte_sha256,
                 date_depot, statut, motif_refus, date_archivage)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, m.getId());
            ps.setString(2, m.getCheminFichier());
            ps.setString(3, m.getNomOriginal());
            ps.setLong(4, m.getTailleOctets());
            ps.setString(5, m.getEmpreinteSha256());
            ps.setString(6, m.getDateDepot() == null ? LocalDate.now().toString() : m.getDateDepot().toString());
            ps.setString(7, (m.getStatut() == null ? StatutMemoire.EN_ATTENTE : m.getStatut()).name());
            ps.setString(8, m.getMotifRefus());
            ps.setString(9, LocalDateTime.now().withNano(0).toString());
            ps.executeUpdate();
        }
    }

    @Override
    public List<MemoireVersion> findByMemoire(int idMemoire) throws SQLException {
        return lister("SELECT * FROM memoire_version WHERE id_memoire = ? ORDER BY id DESC", idMemoire);
    }

    @Override
    public List<MemoireVersion> findAll() throws SQLException {
        return lister("SELECT * FROM memoire_version ORDER BY id", null);
    }

    @Override
    public void changerChemin(int idVersion, String chemin) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE memoire_version SET chemin_fichier = ? WHERE id = ?")) {
            ps.setString(1, chemin);
            ps.setInt(2, idVersion);
            ps.executeUpdate();
        }
    }

    private List<MemoireVersion> lister(String sql, Integer parametre) throws SQLException {
        List<MemoireVersion> versions = new ArrayList<>();

        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            if (parametre != null) {
                ps.setInt(1, parametre);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    MemoireVersion v = new MemoireVersion();
                    v.setId(rs.getInt("id"));
                    v.setIdMemoire(rs.getInt("id_memoire"));
                    v.setCheminFichier(rs.getString("chemin_fichier"));
                    v.setNomOriginal(rs.getString("nom_original"));
                    v.setTailleOctets(rs.getLong("taille_octets"));
                    v.setEmpreinteSha256(rs.getString("empreinte_sha256"));
                    v.setDateDepot(LocalDate.parse(rs.getString("date_depot")));
                    v.setStatut(StatutMemoire.valueOf(rs.getString("statut")));
                    v.setMotifRefus(rs.getString("motif_refus"));
                    v.setDateArchivage(LocalDateTime.parse(rs.getString("date_archivage")));
                    versions.add(v);
                }
            }
        }
        return versions;
    }
}

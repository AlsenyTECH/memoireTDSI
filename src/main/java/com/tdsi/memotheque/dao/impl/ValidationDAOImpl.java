package com.tdsi.memotheque.dao.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.tdsi.memotheque.dao.ValidationDAO;
import com.tdsi.memotheque.model.Memoire;
import com.tdsi.memotheque.util.DatabaseConnection;

/**
 * [M5] Implémentation JDBC de la validation / du refus des mémoires.
 * Les UPDATE portent la condition « statut = 'EN_ATTENTE' » : un mémoire déjà traité ne change plus.
 */
public class ValidationDAOImpl implements ValidationDAO {

    @Override
    public List<Memoire> findEnAttente(Integer idEncadreur) throws SQLException {
        String sql = MemoireDAOImpl.SELECT_COMPLET
                + " WHERE m.statut = 'EN_ATTENTE'"
                + (idEncadreur == null ? "" : " AND m.id_encadreur = ?")
                + " ORDER BY m.date_depot ASC, m.id ASC";

        List<Memoire> memoires = new ArrayList<>();

        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            if (idEncadreur != null) {
                ps.setInt(1, idEncadreur);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    memoires.add(MemoireDAOImpl.mapperMemoire(rs));
                }
            }
        }

        return memoires;
    }

    @Override
    public void valider(int idMemoire, int idValidateur) throws SQLException {
        String sql = """
                UPDATE memoire
                SET statut = 'VALIDE', id_validateur = ?, date_publication = ?, motif_refus = NULL
                WHERE id = ? AND statut = 'EN_ATTENTE'
                """;

        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, idValidateur);
            ps.setString(2, LocalDate.now().toString());
            ps.setInt(3, idMemoire);

            if (ps.executeUpdate() == 0) {
                throw new SQLException("Mémoire introuvable ou déjà traité : " + idMemoire);
            }
        }
    }

    @Override
    public void validerEtSigner(int idMemoire, int idValidateur, LocalDate datePublication,
            String signature, int idCle) throws SQLException {
        String sql = """
                UPDATE memoire
                SET statut = 'VALIDE', id_validateur = ?, date_publication = ?, motif_refus = NULL,
                    signature = ?, id_cle_signature = ?
                WHERE id = ? AND statut = 'EN_ATTENTE'
                """;

        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, idValidateur);
            ps.setString(2, datePublication.toString());
            ps.setString(3, signature);
            ps.setInt(4, idCle);
            ps.setInt(5, idMemoire);

            if (ps.executeUpdate() == 0) {
                throw new SQLException("Mémoire introuvable ou déjà traité : " + idMemoire);
            }
        }
    }

    @Override
    public void refuser(int idMemoire, int idValidateur, String motif) throws SQLException {
        String sql = """
                UPDATE memoire
                SET statut = 'REFUSE', id_validateur = ?, motif_refus = ?, date_publication = NULL,
                    signature = NULL, id_cle_signature = NULL
                WHERE id = ? AND statut = 'EN_ATTENTE'
                """;

        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, idValidateur);
            ps.setString(2, motif);
            ps.setInt(3, idMemoire);

            if (ps.executeUpdate() == 0) {
                throw new SQLException("Mémoire introuvable ou déjà traité : " + idMemoire);
            }
        }
    }
}

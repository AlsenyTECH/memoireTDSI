package com.tdsi.memotheque.dao.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;

import com.tdsi.memotheque.dao.CleSignatureDAO;
import com.tdsi.memotheque.model.CleSignature;
import com.tdsi.memotheque.util.DatabaseConnection;

/** Implémentation JDBC des clés de signature. */
public class CleSignatureDAOImpl implements CleSignatureDAO {

    @Override
    public void insert(CleSignature cle) throws SQLException {
        String sql = """
                INSERT INTO cle_signature
                (id_utilisateur, algorithme, cle_publique, cle_privee_chiffree, sel, iterations,
                 active, date_creation)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            if (cle.getDateCreation() == null) {
                cle.setDateCreation(LocalDateTime.now().withNano(0));
            }
            ps.setInt(1, cle.getIdUtilisateur());
            ps.setString(2, cle.getAlgorithme());
            ps.setString(3, cle.getClePublique());
            ps.setString(4, cle.getClePriveeChiffree());
            ps.setString(5, cle.getSel());
            ps.setInt(6, cle.getIterations());
            ps.setInt(7, cle.isActive() ? 1 : 0);
            ps.setString(8, cle.getDateCreation().toString());
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (!rs.next()) {
                    throw new SQLException("Identifiant de clé non généré.");
                }
                cle.setId(rs.getInt(1));
            }
        }
    }

    @Override
    public CleSignature findById(int id) throws SQLException {
        return lireUne("SELECT * FROM cle_signature WHERE id = ?", id);
    }

    @Override
    public CleSignature findActive(int idUtilisateur) throws SQLException {
        return lireUne("SELECT * FROM cle_signature WHERE id_utilisateur = ? AND active = 1 "
                + "ORDER BY id DESC LIMIT 1", idUtilisateur);
    }

    @Override
    public void changerChiffrement(int id, String clePriveeChiffree, String sel, int iterations)
            throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE cle_signature SET cle_privee_chiffree = ?, sel = ?, iterations = ? WHERE id = ?")) {
            ps.setString(1, clePriveeChiffree);
            ps.setString(2, sel);
            ps.setInt(3, iterations);
            ps.setInt(4, id);
            ps.executeUpdate();
        }
    }

    @Override
    public void desactiver(int idUtilisateur) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE cle_signature SET active = 0 WHERE id_utilisateur = ?")) {
            ps.setInt(1, idUtilisateur);
            ps.executeUpdate();
        }
    }

    private CleSignature lireUne(String sql, int parametre) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, parametre);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                CleSignature cle = new CleSignature();
                cle.setId(rs.getInt("id"));
                cle.setIdUtilisateur(rs.getInt("id_utilisateur"));
                cle.setAlgorithme(rs.getString("algorithme"));
                cle.setClePublique(rs.getString("cle_publique"));
                cle.setClePriveeChiffree(rs.getString("cle_privee_chiffree"));
                cle.setSel(rs.getString("sel"));
                cle.setIterations(rs.getInt("iterations"));
                cle.setActive(rs.getInt("active") == 1);
                cle.setDateCreation(LocalDateTime.parse(rs.getString("date_creation")));
                return cle;
            }
        }
    }
}

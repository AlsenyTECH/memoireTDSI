package com.tdsi.memotheque.dao.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

import com.tdsi.memotheque.dao.DocumentEmisDAO;
import com.tdsi.memotheque.model.DocumentEmis;
import com.tdsi.memotheque.util.DatabaseConnection;

/** Implémentation JDBC du registre des documents émis. */
public class DocumentEmisDAOImpl implements DocumentEmisDAO {

    @Override
    public void insert(DocumentEmis d) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO document_emis (code, type, id_reference, sujet, contenu, signature, empreinte_etat, "
                             + "date_emission, id_emetteur, revoque) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, d.code());
            ps.setString(2, d.type());
            ps.setInt(3, d.idReference());
            ps.setString(4, d.sujet());
            ps.setString(5, d.contenu());
            ps.setString(6, d.signature());
            ps.setString(7, d.empreinteEtat());
            ps.setString(8, d.dateEmission().toString());
            if (d.idEmetteur() == null) {
                ps.setNull(9, java.sql.Types.INTEGER);
            } else {
                ps.setInt(9, d.idEmetteur());
            }
            ps.setInt(10, d.revoque() ? 1 : 0);
            ps.executeUpdate();
        }
    }

    @Override
    public DocumentEmis findByCode(String code) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM document_emis WHERE code = ?")) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? lire(rs) : null;
            }
        }
    }

    @Override
    public DocumentEmis trouver(String type, int idReference, String sujet, String empreinteEtat) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM document_emis WHERE type = ? AND id_reference = ? AND sujet = ? "
                             + "AND empreinte_etat = ? AND revoque = 0 ORDER BY date_emission DESC LIMIT 1")) {
            ps.setString(1, type);
            ps.setInt(2, idReference);
            ps.setString(3, sujet);
            ps.setString(4, empreinteEtat);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? lire(rs) : null;
            }
        }
    }

    private static DocumentEmis lire(ResultSet rs) throws SQLException {
        int emetteur = rs.getInt("id_emetteur");
        Integer idEmetteur = rs.wasNull() ? null : emetteur;
        return new DocumentEmis(rs.getString("code"), rs.getString("type"), rs.getInt("id_reference"),
                rs.getString("sujet"), rs.getString("contenu"), rs.getString("signature"),
                rs.getString("empreinte_etat"), LocalDateTime.parse(rs.getString("date_emission")),
                idEmetteur, rs.getInt("revoque") == 1);
    }
}

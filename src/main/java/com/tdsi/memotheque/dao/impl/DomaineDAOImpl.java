
package com.tdsi.memotheque.dao.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.tdsi.memotheque.dao.DomaineDAO;
import com.tdsi.memotheque.model.Domaine;
import com.tdsi.memotheque.util.DatabaseConnection;

/** [M6] Implémentation JDBC de la gestion des domaines. */
public class DomaineDAOImpl implements DomaineDAO {

    @Override
    public void insert(Domaine d) throws SQLException {
        String sql = "INSERT INTO domaine (nom, description) VALUES (?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, d.getNom());
            ps.setString(2, d.getDescription());

            ps.executeUpdate();
        }
    }

    @Override
    public void update(Domaine d) throws SQLException {
        String sql = """
            UPDATE domaine
            SET nom = ?, description = ?
            WHERE id = ?
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, d.getNom());
            ps.setString(2, d.getDescription());
            ps.setInt(3, d.getId());

            int lignes = ps.executeUpdate();

            if (lignes == 0) {
                throw new SQLException("Domaine introuvable : " + d.getId());
            }
        }
    }

    @Override
    public void delete(int id) throws SQLException {
        if (estUtilise(id)) {
            throw new SQLException(
                "Impossible de supprimer ce domaine : il est utilisé par un mémoire."
            );
        }

        String sql = "DELETE FROM domaine WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);

            int lignes = ps.executeUpdate();

            if (lignes == 0) {
                throw new SQLException("Domaine introuvable : " + id);
            }
        }
    }

    @Override
    public Domaine findById(int id) throws SQLException {
        String sql = """
            SELECT id, nom, description
            FROM domaine
            WHERE id = ?
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Domaine(
                        rs.getInt("id"),
                        rs.getString("nom"),
                        rs.getString("description")
                    );
                }
            }
        }

        return null;
    }

    @Override
    public List<Domaine> findAll() throws SQLException {
        String sql = """
            SELECT id, nom, description
            FROM domaine
            ORDER BY nom ASC
            """;

        List<Domaine> domaines = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Domaine d = new Domaine(
                    rs.getInt("id"),
                    rs.getString("nom"),
                    rs.getString("description")
                );

                domaines.add(d);
            }
        }

        return domaines;
    }

    @Override
    public boolean estUtilise(int idDomaine) throws SQLException {
        String sql = """
            SELECT EXISTS (
                SELECT 1
                FROM memoire
                WHERE id_domaine = ?
            )
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, idDomaine);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) == 1;
            }
        }
    }
}



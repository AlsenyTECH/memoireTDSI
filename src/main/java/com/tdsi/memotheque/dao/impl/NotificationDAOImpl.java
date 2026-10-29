package com.tdsi.memotheque.dao.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.tdsi.memotheque.dao.NotificationDAO;
import com.tdsi.memotheque.model.Notification;
import com.tdsi.memotheque.util.DatabaseConnection;

/** Implémentation JDBC des notifications. */
public class NotificationDAOImpl implements NotificationDAO {

    @Override
    public void insert(Notification n) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO notification (id_utilisateur, message, lue, date_heure) VALUES (?, ?, ?, ?)")) {
            if (n.getDateHeure() == null) {
                n.setDateHeure(LocalDateTime.now().withNano(0));
            }
            ps.setInt(1, n.getIdUtilisateur());
            ps.setString(2, n.getMessage());
            ps.setInt(3, n.isLue() ? 1 : 0);
            ps.setString(4, n.getDateHeure().toString());
            ps.executeUpdate();
        }
    }

    @Override
    public List<Notification> findByUtilisateur(int idUtilisateur, int limite) throws SQLException {
        List<Notification> liste = new ArrayList<>();
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM notification WHERE id_utilisateur = ? ORDER BY lue ASC, id DESC LIMIT ?")) {
            ps.setInt(1, idUtilisateur);
            ps.setInt(2, Math.max(1, Math.min(limite, 500)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Notification n = new Notification();
                    n.setId(rs.getInt("id"));
                    n.setIdUtilisateur(rs.getInt("id_utilisateur"));
                    n.setMessage(rs.getString("message"));
                    n.setLue(rs.getInt("lue") == 1);
                    n.setDateHeure(LocalDateTime.parse(rs.getString("date_heure")));
                    liste.add(n);
                }
            }
        }
        return liste;
    }

    @Override
    public int compterNonLues(int idUtilisateur) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM notification WHERE id_utilisateur = ? AND lue = 0")) {
            ps.setInt(1, idUtilisateur);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    @Override
    public void marquerToutesLues(int idUtilisateur) throws SQLException {
        executer("UPDATE notification SET lue = 1 WHERE id_utilisateur = ? AND lue = 0", idUtilisateur);
    }

    @Override
    public void supprimerLues(int idUtilisateur) throws SQLException {
        executer("DELETE FROM notification WHERE id_utilisateur = ? AND lue = 1", idUtilisateur);
    }

    private void executer(String sql, int idUtilisateur) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idUtilisateur);
            ps.executeUpdate();
        }
    }
}

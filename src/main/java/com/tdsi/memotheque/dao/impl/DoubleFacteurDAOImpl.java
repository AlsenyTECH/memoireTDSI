package com.tdsi.memotheque.dao.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

import com.tdsi.memotheque.dao.DoubleFacteurDAO;
import com.tdsi.memotheque.util.DatabaseConnection;

/** Implémentation JDBC de la double authentification. */
public class DoubleFacteurDAOImpl implements DoubleFacteurDAO {

    @Override
    public Enregistrement lire(int idUtilisateur) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT secret_chiffre, dernier_pas FROM double_facteur WHERE id_utilisateur = ?")) {
            ps.setInt(1, idUtilisateur);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new Enregistrement(idUtilisateur, rs.getString(1), rs.getLong(2)) : null;
            }
        }
    }

    @Override
    public void enregistrer(int idUtilisateur, String secretChiffre) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO double_facteur (id_utilisateur, secret_chiffre, dernier_pas, date_activation) "
                             + "VALUES (?, ?, -1, ?) ON CONFLICT(id_utilisateur) DO UPDATE SET "
                             + "secret_chiffre = excluded.secret_chiffre, dernier_pas = -1, "
                             + "date_activation = excluded.date_activation")) {
            ps.setInt(1, idUtilisateur);
            ps.setString(2, secretChiffre);
            ps.setString(3, LocalDateTime.now().withNano(0).toString());
            ps.executeUpdate();
        }
    }

    @Override
    public boolean consommerPas(int idUtilisateur, long pas) throws SQLException {
        // Mise à jour conditionnelle atomique : un même code ne peut servir qu'une fois.
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE double_facteur SET dernier_pas = ? WHERE id_utilisateur = ? AND dernier_pas < ?")) {
            ps.setLong(1, pas);
            ps.setInt(2, idUtilisateur);
            ps.setLong(3, pas);
            return ps.executeUpdate() == 1;
        }
    }

    @Override
    public void supprimer(int idUtilisateur) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM double_facteur WHERE id_utilisateur = ?")) {
            ps.setInt(1, idUtilisateur);
            ps.executeUpdate();
        }
    }
}

package com.tdsi.memotheque.dao.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.tdsi.memotheque.dao.ParametreDAO;
import com.tdsi.memotheque.util.DatabaseConnection;

/** Implémentation JDBC des paramètres. */
public class ParametreDAOImpl implements ParametreDAO {

    @Override
    public String lire(String cle) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT valeur FROM parametre WHERE cle = ?")) {
            ps.setString(1, cle);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    @Override
    public void ecrire(String cle, String valeur) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO parametre (cle, valeur) VALUES (?, ?) "
                             + "ON CONFLICT(cle) DO UPDATE SET valeur = excluded.valeur")) {
            ps.setString(1, cle);
            ps.setString(2, valeur);
            ps.executeUpdate();
        }
    }
}
